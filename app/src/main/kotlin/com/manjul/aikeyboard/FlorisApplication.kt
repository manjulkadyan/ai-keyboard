/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.manjul.aikeyboard

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.util.Log
import androidx.core.os.UserManagerCompat
import com.manjul.aikeyboard.app.FlorisPreferenceModel
import com.manjul.aikeyboard.app.FlorisPreferenceStore
import com.manjul.aikeyboard.ime.clipboard.ClipboardManager
import com.manjul.aikeyboard.ime.core.SubtypeManager
import com.manjul.aikeyboard.ime.dictionary.DictionaryManager
import com.manjul.aikeyboard.ime.editor.EditorInstance
import com.manjul.aikeyboard.ime.keyboard.KeyboardManager
import com.manjul.aikeyboard.ime.media.emoji.FlorisEmojiCompat
import com.manjul.aikeyboard.ime.nlp.NlpManager
import com.manjul.aikeyboard.ime.text.gestures.GlideTypingManager
import com.manjul.aikeyboard.ime.theme.ThemeManager
import com.manjul.aikeyboard.lib.cache.CacheManager
import com.manjul.aikeyboard.lib.crashutility.CrashUtility
import com.manjul.aikeyboard.lib.devtools.Flog
import com.manjul.aikeyboard.lib.devtools.LogTopic
import com.manjul.aikeyboard.lib.devtools.flogError
import com.manjul.aikeyboard.lib.ext.ExtensionManager
import dev.patrickgold.jetpref.datastore.runtime.initAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.florisboard.lib.kotlin.io.deleteContentsRecursively
import org.florisboard.lib.kotlin.tryOrNull
import org.florisboard.libnative.dummyAdd
// FORK(S1-policy): import for the fork settings store
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
// FORK(S1-analytics): imports for the analytics relay
import com.manjul.aikeyboard.core.analytics.BatchingRelay
import com.manjul.aikeyboard.core.analytics.LogcatSink
import com.manjul.aikeyboard.core.analytics.NoopSink
// FORK(S1-whisper): imports for the Whisper model download and unloading
import androidx.work.Configuration
import com.manjul.aikeyboard.core.voice.whisper.ModelDownloadWorkerFactory
import com.manjul.aikeyboard.fork.voice.WhisperVoice
// FORK(S2-mlkit): import for the translation core
import com.manjul.aikeyboard.fork.translate.TranslationCore
// FORK(S2-packs): imports for the offline translation packs
import android.app.Activity
import android.os.Bundle
import androidx.work.DelegatingWorkerFactory
import androidx.work.WorkManager
import com.manjul.aikeyboard.core.translate.mlkit.MlKitRemoteModels
import com.manjul.aikeyboard.core.translate.mlkit.TranslateModelDownloadWorkerFactory
import com.manjul.aikeyboard.core.translate.mlkit.TranslateModelStore
import kotlinx.coroutines.SupervisorJob
// FORK(S2-translator): imports for Translator history and the shared TextToSpeech
import com.manjul.aikeyboard.core.database.AppDatabase
import com.manjul.aikeyboard.core.database.TranslationHistoryRepository
import com.manjul.aikeyboard.core.database.UserWordRepository // FORK(S3-userdict)
import com.manjul.aikeyboard.core.tts.TtsManager
import com.manjul.aikeyboard.fork.suggest.SuggestContext // FORK(S3-suggest)
import com.manjul.aikeyboard.fork.ai.AiAppGraph // FORK(S3-ai)
import java.lang.ref.WeakReference

/**
 * Global weak reference for the [FlorisApplication] class. This is needed as in certain scenarios an application
 * reference is needed, but the Android framework hasn't finished setting up
 */
private var FlorisApplicationReference = WeakReference<FlorisApplication?>(null)

@Suppress("unused")
class FlorisApplication : Application(), Configuration.Provider { // FORK(S1-whisper): on-demand WorkManager
    companion object {
        init {
            try {
                System.loadLibrary("fl_native")
            } catch (_: Exception) {
            }
        }
    }

    private val mainHandler by lazy { Handler(mainLooper) }
    private val scope = CoroutineScope(Dispatchers.Default)
    val preferenceStoreLoaded = MutableStateFlow(false)

    val cacheManager = lazy { CacheManager(this) }
    val clipboardManager = lazy { ClipboardManager(this) }
    val editorInstance = lazy { EditorInstance(this) }
    val extensionManager = lazy { ExtensionManager(this) }
    val glideTypingManager = lazy { GlideTypingManager(this) }
    val keyboardManager = lazy { KeyboardManager(this) }
    val nlpManager = lazy { NlpManager(this) }
    val subtypeManager = lazy { SubtypeManager(this) }
    val themeManager = lazy { ThemeManager(this) }
    // FORK(S1-policy): fork-owned settings (DataStore), read by the policy controller
    val featureSettingsStore = lazy { FeatureSettingsStore(this) }
    // FORK(S1-analytics): batched, consent-aware analytics relay (Logcat in debug, no-op in release)
    val analytics = lazy {
        BatchingRelay(
            sink = if (BuildConfig.DEBUG) LogcatSink else NoopSink,
            clock = System::currentTimeMillis,
            scope = scope,
            optOut = { featureSettingsStore.value.current.analyticsOptOut },
            // null policy = no IME field context (app-side events): not minimal, by design
            policy = { FlorisImeService.policyControllerOrNull()?.decision?.value },
        )
    }
    // FORK(S1-whisper): Whisper model, idle unloader and model manager; created on first Whisper use
    val whisperVoice = lazy { WhisperVoice(this) { analytics.value::log } }
    // FORK(S2-packs): offline translation packs (ML Kit models, WorkManager job); both start on first use
    val translateModelStore = lazy {
        TranslateModelStore(
            models = MlKitRemoteModels(this),
            workManager = { WorkManager.getInstance(this) },
            settings = featureSettingsStore.value,
            analytics = { analytics.value.log(it) },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
    }
    // FORK(S2-mlkit): router, ML Kit engine, language ID and translator pool; ML Kit itself starts on first use
    // FORK(S2-packs): model presence and the bar's downloads go through the model store
    val translation = lazy { TranslationCore(this) { translateModelStore.value } }
    // FORK(S2-translator): fork Room database (databases/app_database, never backed up), opened on first use
    val appDatabase = lazy { AppDatabase.new(this) }
    val translationHistory = lazy { TranslationHistoryRepository(appDatabase.value.translationHistoryDao()) }
    // FORK(S3-userdict): on-device user dictionary (user_word); opened on first use, written on a background worker
    val userWordRepository = lazy {
        UserWordRepository(
            appDatabase.value.userWordDao(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
    }
    // FORK(S3-suggest): field facts for the suggestion provider, set by FlorisImeService (fails closed until then)
    val suggestContext = SuggestContext()
    // FORK(S2-translator): the process's one TextToSpeech holder; the engine binds on the first acquire()
    val ttsManager = lazy { TtsManager(this) }
    // FORK(S3-ai): config, kb-api tokens, cloud engine, LRU, pause gate, reports; no HTTP client or Firebase until used
    val aiAppGraph = lazy { AiAppGraph.create(this) }

    // FORK(S1-whisper): WorkManager initialises on demand (its startup initializer is removed in the manifest)
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(
                DelegatingWorkerFactory().apply {
                    addFactory(ModelDownloadWorkerFactory { whisperVoice.value.models })
                    // FORK(S2-packs): translation model downloads
                    addFactory(TranslateModelDownloadWorkerFactory { translateModelStore.value })
                },
            )
            .build()

    // FORK(S1-whisper): free the Whisper model under memory pressure; never creates it, never blocks
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (whisperVoice.isInitialized()) whisperVoice.value.unloader.onTrimMemory(level)
        // FORK(S2-mlkit): close the loaded ML Kit Translator at RUNNING_LOW and above; never creates the core
        if (translation.isInitialized()) translation.value.pool.onTrimMemory(level)
    }

    override fun onCreate() {
        super.onCreate()
        FlorisApplicationReference = WeakReference(this)
        try {
            Flog.install(
                context = this,
                isFloggingEnabled = BuildConfig.DEBUG,
                flogTopics = LogTopic.ALL,
                // FORK(S1-privacy-logs): no user content in any log (PC-10); see VERBOSE_FLOG in app/build.gradle.kts
                flogLevels = if (BuildConfig.VERBOSE_FLOG) Flog.LEVEL_ALL else Flog.LEVEL_ERROR or Flog.LEVEL_WARNING,
                flogOutputs = Flog.OUTPUT_CONSOLE,
            )
            CrashUtility.install(this)
            FlorisEmojiCompat.init(this)
            flogError { "dummy result: ${dummyAdd(3,4)}" }

            if (!UserManagerCompat.isUserUnlocked(this)) {
                cacheDir?.deleteContentsRecursively()
                extensionManager.value.init()
                registerReceiver(BootComplete(), IntentFilter(Intent.ACTION_USER_UNLOCKED))
                return
            }

            init()
        } catch (e: Exception) {
            CrashUtility.stageException(e)
            return
        }
    }

    fun init() {
        featureSettingsStore.value // FORK(S1-policy): start loading the fork settings at process start
        cacheDir?.deleteContentsRecursively()
        scope.launch {
            val result = FlorisPreferenceStore.initAndroid(
                context = this@FlorisApplication,
                datastoreName = FlorisPreferenceModel.NAME,
            )
            Log.i("PREFS", result.toString())
            preferenceStoreLoaded.value = true
        }
        extensionManager.value.init()
        clipboardManager.value.initializeForContext(this)
        DictionaryManager.init(this)
        registerActivityLifecycleCallbacks(PackRefresher()) // FORK(S2-packs): refresh model states on app resume
    }

    // FORK(S2-packs): re-reads ML Kit's models when an app screen resumes (plan decision 4); never from the IME
    private inner class PackRefresher : ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            scope.launch { runCatching { translateModelStore.value.refresh() } }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    private inner class BootComplete : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            if (intent.action == Intent.ACTION_USER_UNLOCKED) {
                try {
                    unregisterReceiver(this)
                } catch (e: Exception) {
                    flogError { e.toString() }
                }
                mainHandler.post { init() }
            }
        }
    }
}

private tailrec fun Context.florisApplication(): FlorisApplication {
    return when (this) {
        is FlorisApplication -> this
        is ContextWrapper -> when {
            this.baseContext != null -> this.baseContext.florisApplication()
            else -> FlorisApplicationReference.get()!!
        }
        else -> tryOrNull { this.applicationContext as FlorisApplication } ?: FlorisApplicationReference.get()!!
    }
}

fun Context.appContext() = lazyOf(this.florisApplication())

fun Context.cacheManager() = this.florisApplication().cacheManager

fun Context.clipboardManager() = this.florisApplication().clipboardManager

fun Context.editorInstance() = this.florisApplication().editorInstance

fun Context.extensionManager() = this.florisApplication().extensionManager

fun Context.glideTypingManager() = this.florisApplication().glideTypingManager

fun Context.keyboardManager() = this.florisApplication().keyboardManager

fun Context.nlpManager() = this.florisApplication().nlpManager

fun Context.subtypeManager() = this.florisApplication().subtypeManager

fun Context.themeManager() = this.florisApplication().themeManager

// FORK(S1-policy): accessor for the fork settings store
fun Context.featureSettingsStore() = this.florisApplication().featureSettingsStore

// FORK(S1-whisper): accessor for the app-wide Whisper pieces
fun Context.whisperVoice() = this.florisApplication().whisperVoice

// FORK(S2-mlkit): accessor for the app-wide translation core
fun Context.translation() = this.florisApplication().translation

// FORK(S2-packs): accessor for the offline translation packs
fun Context.translateModelStore() = this.florisApplication().translateModelStore

// FORK(S2-translator): accessors for Translator history and the shared TextToSpeech
fun Context.translationHistory() = this.florisApplication().translationHistory

fun Context.ttsManager() = this.florisApplication().ttsManager

// FORK(S3-userdict): accessor for the user dictionary
fun Context.userWordRepository() = this.florisApplication().userWordRepository

// FORK(S3-suggest): accessor for the suggestion field facts
fun Context.suggestContext() = this.florisApplication().suggestContext

// FORK(S3-ai): accessor for the app-wide AI pieces
fun Context.aiAppGraph() = this.florisApplication().aiAppGraph
