/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.processtext

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.app.AppTheme
import com.manjul.aikeyboard.app.FlorisAppActivity
import com.manjul.aikeyboard.app.apptheme.FlorisAppTheme
import com.manjul.aikeyboard.appContext
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.translate.LangPair
import com.manjul.aikeyboard.core.translate.SourceResolver
import com.manjul.aikeyboard.core.translate.mlkit.MlKitLanguages
import com.manjul.aikeyboard.core.translate.mlkit.TranslateModelStore
import com.manjul.aikeyboard.core.tts.TtsAvailability
import com.manjul.aikeyboard.core.tts.TtsManager
import com.manjul.aikeyboard.featureSettingsStore
import com.manjul.aikeyboard.fork.onboarding.InputMethodImeStatus
import com.manjul.aikeyboard.fork.routes.RouteRegistry
import com.manjul.aikeyboard.translateModelStore
import com.manjul.aikeyboard.translation
import com.manjul.aikeyboard.translationHistory
import com.manjul.aikeyboard.ttsManager
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.ProvideLocalizedResources

/**
 * The "Translate" text-selection action and text share target (PRD FR-14, S2-processtext). Exported, so the intent
 * is untrusted: [ProcessTextInput.parse] accepts only `PROCESS_TEXT` and `SEND` `text/plain` with text, anything else
 * finishes at once with no UI. A result (the translation in `EXTRA_PROCESS_TEXT`) is returned only by Replace, which
 * exists only for an editable `PROCESS_TEXT` selection; every other way out is `RESULT_CANCELED`. No ads.
 */
class TranslateTextActivity : ComponentActivity() {
    @VisibleForTesting
    internal class Deps(
        val sheet: (scope: CoroutineScope) -> TranslateSheetViewModel.Deps,
        val installVoiceIntent: () -> Intent,
        /** Languages offered as the target. */
        val targets: () -> List<Lang>,
    )

    /** Keeps the view model (and its scope) across rotation. */
    internal class Holder(deps: Deps, input: ProcessTextInput) : ViewModel() {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm = TranslateSheetViewModel(input, deps.sheet(scope), scope)

        override fun onCleared() {
            vm.clear()
            scope.cancel()
        }
    }

    private lateinit var deps: Deps
    private lateinit var input: ProcessTextInput
    private lateinit var vm: TranslateSheetViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Every way out except Replace returns no text.
        setResult(RESULT_CANCELED)
        input = ProcessTextInput.parse(intent) ?: run {
            finish()
            return
        }
        deps = depsOverride ?: defaultDeps(this)
        val holder = ViewModelProvider(this, viewModelFactory { initializer { Holder(deps, input) } })[Holder::class.java]
        vm = holder.vm
        if (savedInstanceState == null) vm.onOpened()
        val targets = deps.targets()
        enableEdgeToEdge()
        setContent {
            ProvideLocalizedResources(this, appName = R.string.app_name) {
                FlorisAppTheme(AppTheme.AUTO) {
                    val state by vm.state.collectAsState()
                    TranslateSheet(
                        state = state,
                        canReplace = input.canReplace,
                        truncated = input.truncated,
                        targets = targets,
                        actions = TranslateSheetActions(
                            onDismiss = ::finish,
                            onPickTarget = vm::pickTarget,
                            onRetry = vm::translate,
                            onDownload = vm::download,
                            onCopy = ::copy,
                            onReplace = ::replace,
                            onSpeak = ::speak,
                            onOpenInApp = ::openInApp,
                            onEnableKeyboard = ::openKeyboardSettings,
                            onDismissEnableKeyboard = vm::dismissEnableKeyboard,
                        ),
                    )
                }
            }
        }
    }

    private fun copy() {
        val text = vm.onCopy() ?: return
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        // The label is ours; the clip itself is the translation the user asked to copy.
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.process_text__label), text))
        // Android 13+ confirms clipboard writes itself.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.process_text__copied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun replace() {
        val text = vm.onReplace() ?: return
        setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, text))
        finish()
    }

    private fun speak() {
        lifecycleScope.launch {
            if (vm.speak() == SpeakOutcome.MISSING_VOICE) start(deps.installVoiceIntent())
        }
    }

    private fun openInApp() {
        val text = vm.onOpenInApp()
        start(translatorIntent(this, text))
        finish()
    }

    private fun openKeyboardSettings() {
        vm.dismissEnableKeyboard()
        start(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
    }

    private fun start(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // No settings or TTS screen on this device: nothing to open.
        }
    }

    companion object {
        @VisibleForTesting
        internal var depsOverride: Deps? = null

        /** Opens the Translator with [text] through the route allow-list. */
        fun translatorIntent(context: Context, text: String): Intent =
            Intent(context, FlorisAppActivity::class.java)
                .putExtra(RouteRegistry.EXTRA_OPEN_SCREEN, RouteRegistry.TRANSLATOR)
                .putExtra(RouteRegistry.EXTRA_TRANSLATOR_TEXT, text)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        private fun defaultDeps(context: Context): Deps {
            val app = context.applicationContext
            return Deps(
                sheet = { scope ->
                    val core = app.translation().value
                    val resolver = SourceResolver(core.detector)
                    TranslateSheetViewModel.Deps(
                        store = app.featureSettingsStore().value,
                        translate = { req, ctx, last -> core.router.translate(req, ctx, last) },
                        detect = { text, fallback -> resolver.resolve(text, null, fallback) },
                        models = StoreSheetModels(app.translateModelStore().value),
                        speaker = TtsSheetSpeaker(app.ttsManager().value),
                        history = { entry -> app.translationHistory().value.upsertSession(null, entry) },
                        imeEnabled = InputMethodImeStatus(app, scope).enabled,
                        analytics = app.appContext().value.analytics.value::log,
                        clock = System::currentTimeMillis,
                    )
                },
                installVoiceIntent = { app.ttsManager().value.installVoiceIntent() },
                targets = {
                    val locale = Locale.getDefault()
                    MlKitLanguages.all.sortedBy { displayName(it, locale).lowercase(locale) }
                },
            )
        }
    }
}

private class StoreSheetModels(private val store: TranslateModelStore) : SheetModels {
    override val states = store.states

    override suspend fun refresh() = store.refresh()

    override fun download(pair: LangPair) = store.download(pair)
}

/** Holds the shared `TtsManager` from the first Speak until the sheet goes away. */
private class TtsSheetSpeaker(private val tts: TtsManager) : SheetSpeaker {
    private var acquired = false

    override suspend fun availability(lang: Lang): TtsAvailability {
        if (!acquired) {
            acquired = true
            tts.acquire()
        }
        return tts.availability(Locale.forLanguageTag(lang.tag))
    }

    override fun speak(text: String, lang: Lang) = tts.speak(text, Locale.forLanguageTag(lang.tag))

    override fun stop() {
        if (acquired) tts.stop()
    }

    override fun release() {
        if (acquired) {
            acquired = false
            tts.release()
        }
    }
}
