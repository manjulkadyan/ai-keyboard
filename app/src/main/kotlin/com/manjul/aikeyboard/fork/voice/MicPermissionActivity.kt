/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.app.AppTheme
import com.manjul.aikeyboard.app.apptheme.FlorisAppTheme
import com.manjul.aikeyboard.appContext
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.core.voice.EngineKind
import com.manjul.aikeyboard.core.voice.platform.EngineChoice
import com.manjul.aikeyboard.core.voice.platform.SpeechEngineSelector
import com.manjul.aikeyboard.featureSettingsStore
import com.manjul.aikeyboard.whisperVoice
import com.manjul.aikeyboard.fork.onboarding.DisclosureEngineKind
import com.manjul.aikeyboard.fork.onboarding.VoiceDisclosureSheet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.florisboard.lib.compose.stringRes

/**
 * Microphone permission trampoline for the keyboard, which cannot ask for permissions itself. Opened only by a tap
 * on the keyboard's permission chip. Shows the voice notice first unless it was agreed to before, asks for
 * RECORD_AUDIO, and finishes. On denial it explains where to turn the microphone on.
 *
 * It never starts dictation: the user taps the mic again, so a grant cannot start recording on its own.
 */
class MicPermissionActivity : ComponentActivity() {
    @VisibleForTesting
    internal class Deps(
        val store: FeatureSettingsStore,
        val analytics: (AnalyticsEvent) -> Unit,
        val engineKind: suspend (lang: String) -> DisclosureEngineKind,
        val clock: () -> Long,
    )

    private enum class Screen { NONE, DISCLOSURE, RATIONALE }

    private lateinit var deps: Deps
    private var screen by mutableStateOf(Screen.NONE)
    private var disclosureKind = DisclosureEngineKind.SYSTEM_SERVICE
    private var requested = false

    private val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        deps.analytics(EventCatalog.voicePermissionResult(granted))
        if (granted) finish() else screen = Screen.RATIONALE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            finish()
            return
        }
        deps = depsOverride ?: defaultDeps(this)
        requested = savedInstanceState?.getBoolean(KEY_REQUESTED) ?: false
        // The rationale has no other way back after a configuration change (the result was already delivered).
        // The notice is rebuilt by the flow below, with the engine it names.
        if (savedInstanceState?.getBoolean(KEY_RATIONALE) == true) screen = Screen.RATIONALE
        setContent {
            ProvideLocalizedResources(this, appName = R.string.app_name) {
                FlorisAppTheme(AppTheme.AUTO) {
                    when (screen) {
                        Screen.NONE -> Unit
                        Screen.DISCLOSURE -> VoiceDisclosureSheet(
                            engineKind = { disclosureKind },
                            store = deps.store,
                            clock = deps.clock,
                            onAgree = ::request,
                            onDismiss = ::finish,
                        )
                        Screen.RATIONALE -> AlertDialog(
                            onDismissRequest = ::finish,
                            title = { Text(stringRes(R.string.voice__permission_denied_title)) },
                            text = { Text(stringRes(R.string.voice__permission_denied_body)) },
                            confirmButton = {
                                TextButton(onClick = ::openAppSettings) {
                                    Text(stringRes(R.string.voice__permission_open_settings))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = ::finish) { Text(stringRes(R.string.voice__permission_not_now)) }
                            },
                        )
                    }
                }
            }
        }
        // After a configuration change the pending request (if any) comes back through the launcher.
        if (requested) return
        val lang = intent.getStringExtra(EXTRA_LANG) ?: DEFAULT_LANG
        lifecycleScope.launch {
            if (deps.store.flow.first().consentVoiceNoticeAt > 0) {
                request()
            } else {
                disclosureKind = deps.engineKind(lang)
                screen = Screen.DISCLOSURE
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_REQUESTED, requested)
        outState.putBoolean(KEY_RATIONALE, screen == Screen.RATIONALE)
    }

    private fun request() {
        screen = Screen.NONE
        requested = true
        launcher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    companion object {
        /** BCP-47 tag of the keyboard language, to name the recognizer that will be used in the notice. */
        const val EXTRA_LANG = "com.manjul.aikeyboard.extra.VOICE_LANG"
        private const val KEY_REQUESTED = "requested"
        private const val KEY_RATIONALE = "rationale"
        private const val DEFAULT_LANG = "en"

        @VisibleForTesting
        internal var depsOverride: Deps? = null

        fun intent(context: Context, lang: String): Intent =
            Intent(context, MicPermissionActivity::class.java)
                .putExtra(EXTRA_LANG, lang)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        private fun defaultDeps(context: Context): Deps {
            val store = context.featureSettingsStore().value
            val selector = SpeechEngineSelector(context.whisperVoice().value.capabilities(context))
            return Deps(
                store = store,
                analytics = context.appContext().value.analytics.value::log,
                engineKind = { lang ->
                    selector.choose(lang, store.current.voiceEnginePreference, isOnline(context)).toDisclosureKind()
                },
                clock = System::currentTimeMillis,
            )
        }
    }
}

/**
 * The notice names the recognizer the keyboard will use. No engine yet: assume audio may leave the device, unless
 * the engine preference keeps it on the phone.
 */
internal fun EngineChoice.toDisclosureKind(): DisclosureEngineKind = when (this) {
    is EngineChoice.Use -> when (kind) {
        EngineKind.SYSTEM_ON_DEVICE -> DisclosureEngineKind.ON_DEVICE
        EngineKind.SYSTEM_DEFAULT -> DisclosureEngineKind.SYSTEM_SERVICE
        EngineKind.WHISPER -> DisclosureEngineKind.WHISPER
    }
    is EngineChoice.Unavailable ->
        if (localOnly) DisclosureEngineKind.ON_DEVICE else DisclosureEngineKind.SYSTEM_SERVICE
}
