/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.inputmethod.InputConnection
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.EndReason
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.core.voice.EngineKind
import com.manjul.aikeyboard.core.voice.SpeechEngine
import com.manjul.aikeyboard.core.voice.SpeechSession
import com.manjul.aikeyboard.core.voice.SpokenPunctuation
import com.manjul.aikeyboard.core.voice.VoiceTimings
import com.manjul.aikeyboard.core.voice.platform.AudioFocusHolder
import com.manjul.aikeyboard.core.voice.platform.PlatformSpeechEngine
import com.manjul.aikeyboard.core.voice.platform.RecognizerCapabilities
import com.manjul.aikeyboard.core.voice.platform.SpeechEngineSelector
import com.manjul.aikeyboard.fork.policy.ImePolicyController
import com.manjul.aikeyboard.whisperVoice
import com.manjul.aikeyboard.ime.editing.ComposingSpanController
import com.manjul.aikeyboard.ime.voiceui.FirstDictationFlag
import com.manjul.aikeyboard.ime.voiceui.VoiceController
import com.manjul.aikeyboard.ime.voiceui.VoiceEngines
import com.manjul.aikeyboard.ime.voiceui.VoiceRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Voice typing for one IME service instance: wires [VoiceController] to the policy controller, the settings
 * store, the input connection and the platform recognizers. Created in `FlorisImeService.onCreate`, main thread only.
 *
 * Every restriction the policy controller reports (new field, more restrictive restart, no field) cancels the
 * session, and so does hiding the keyboard ([onFinishInputView]).
 */
class VoiceImeSession(
    private val context: Context,
    private val policy: ImePolicyController,
    private val store: FeatureSettingsStore,
    analytics: (AnalyticsEvent) -> Unit,
    connection: () -> InputConnection?,
    isRawInputEditor: () -> Boolean,
    setOwnsComposing: (Boolean) -> Unit,
    private val lang: () -> String,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Resolved on first voice use, so the IME's onCreate does not create the app's Whisper pieces. */
    caps: () -> RecognizerCapabilities = { context.whisperVoice().value.capabilities(context) },
    private val engineFactory: (EngineKind, segmented: Boolean) -> SpeechEngine = { kind, segmented ->
        when (kind) {
            EngineKind.WHISPER -> context.whisperVoice().value.engine(scope)
            else -> PlatformSpeechEngine(context, onDevice = kind == EngineKind.SYSTEM_ON_DEVICE, segmented = segmented)
        }
    },
    private val online: () -> Boolean = { isOnline(context) },
) {
    private val target = ImeTextTarget(connection, isRawInputEditor, setOwnsComposing)

    val span = ComposingSpanController(target)

    val controller = VoiceController(
        scope = scope,
        sessionFactory = { factory ->
            val settings = store.current
            SpeechSession(
                engineFactory = factory,
                scope = scope,
                timings = VoiceTimings(silenceMs = settings.voiceSilenceMs.toLong()),
                punctuation = SpokenPunctuation,
                clock = clock,
                punctuationEnabled = settings.spokenPunctuation,
            )
        },
        engines = object : VoiceEngines {
            override fun supports(kind: EngineKind) = true
            override fun create(kind: EngineKind, segmented: Boolean) = engineFactory(kind, segmented)
        },
        selector = SpeechEngineSelector(LazyCapabilities(caps)),
        request = {
            VoiceRequest(
                lang = lang(),
                preference = store.current.voiceEnginePreference,
                online = online(),
            )
        },
        permission = {
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        },
        decision = { policy.decision.value },
        generation = { policy.generation.value },
        focus = AudioFocusHolder(context.getSystemService(AudioManager::class.java)),
        span = span,
        analytics = analytics,
        firstDictation = object : FirstDictationFlag {
            override val logged: Boolean get() = store.current.firstDictationAt > 0
            override fun markLogged() {
                val now = clock()
                scope.launch { store.update { it.copy(firstDictationAt = now) } }
            }
        },
        launchPermission = {
            // Only reached from the permission chip tap; the IME has no activity task of its own.
            context.startActivity(MicPermissionActivity.intent(context, lang()))
        },
        clock = clock,
        downloadSystemModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            { tag -> triggerSystemModelDownload(context, tag) }
        } else {
            null
        },
    )

    init {
        policy.addRestrictionListener { controller.cancel(EndReason.FIELD_CHANGE.value) }
    }

    /**
     * A new field (not a restart) has a new input connection: a region we owned belonged to the old one. Call
     * before the policy controller's onStartInput, whose restriction cancels the session.
     */
    fun onStartInput(restarting: Boolean) {
        if (!restarting) target.forgetOwnedRegion()
    }

    /**
     * Field change (R39): call before `InputMethodService.onFinishInput`, which finishes the composing region, so
     * the partial is discarded as on hide (R25) instead of being committed into the old field.
     */
    fun onFinishInput() {
        controller.cancel(EndReason.FIELD_CHANGE.value)
    }

    fun onFinishInputView() {
        controller.cancel(EndReason.FIELD_CHANGE.value)
    }

    fun onSelectionUpdate(newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        span.onSelectionUpdate(newSelStart, newSelEnd, candidatesStart, candidatesEnd)
    }

    fun destroy() {
        controller.destroy()
    }

    private companion object {
        /** Asks the on-device recognizer to download [tag]'s model; the system shows its own progress UI. */
        fun triggerSystemModelDownload(context: Context, tag: String) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
            if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) return
            val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            try {
                recognizer.triggerModelDownload(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag),
                )
            } finally {
                recognizer.destroy()
            }
        }
    }
}

/** Resolves [provider] on the first capability question and keeps the answer. */
private class LazyCapabilities(provider: () -> RecognizerCapabilities) : RecognizerCapabilities {
    private val caps by lazy(provider)
    override val sdkInt: Int get() = caps.sdkInt
    override fun isRecognitionAvailable() = caps.isRecognitionAvailable()
    override fun isOnDeviceAvailable() = caps.isOnDeviceAvailable()
    override suspend fun onDeviceSupports(lang: String): Boolean? = caps.onDeviceSupports(lang)
    override fun whisperModelReady(lang: String) = caps.whisperModelReady(lang)
    override fun whisperLangEnabled(lang: String) = caps.whisperLangEnabled(lang)
}

/** Whether a network is up, for the engine choice (a server recognizer needs one). */
internal fun isOnline(context: Context): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
