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
    // FORK(S2-mlkit): router, ML Kit engine, language ID and translator pool; ML Kit itself starts on first use
    // FORK(S2-bar): the bar's model downloads follow the Wi-Fi-only setting
    val translation = lazy { TranslationCore(this) { featureSettingsStore.value.current.translateWifiOnly } }

    // FORK(S1-whisper): WorkManager initialises on demand (its startup initializer is removed in the manifest)
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(ModelDownloadWorkerFactory { whisperVoice.value.models })
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
