/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translator

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.core.datastore.VoiceEnginePreference
import com.manjul.aikeyboard.core.voice.EngineKind
import com.manjul.aikeyboard.core.voice.SpeechSession
import com.manjul.aikeyboard.core.voice.SpokenPunctuation
import com.manjul.aikeyboard.core.voice.VoiceOutput
import com.manjul.aikeyboard.core.voice.VoiceState
import com.manjul.aikeyboard.core.voice.VoiceTimings
import com.manjul.aikeyboard.core.voice.platform.EngineChoice
import com.manjul.aikeyboard.core.voice.platform.PlatformRecognizerCapabilities
import com.manjul.aikeyboard.core.voice.platform.PlatformSpeechEngine
import com.manjul.aikeyboard.core.voice.platform.RecognizerCapabilities
import com.manjul.aikeyboard.core.voice.platform.SpeechEngineSelector
import com.manjul.aikeyboard.fork.voice.isOnline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Translator screen's microphone. Heard text is user content: implementations never log it.
 */
interface TranslatorMic {
    val state: StateFlow<MicState>

    /**
     * Starts listening in [lang]. [onText] gets the text heard so far in this run, replaced on every partial
     * (`final = false`) and settled once per recognized utterance (`final = true`).
     */
    fun start(lang: String, onText: (text: String, final: Boolean) -> Unit)

    /** Stops listening and keeps what was heard. */
    fun stop()

    /** Stops and releases the recognizer; the screen calls it when its view model is cleared. */
    fun destroy()
}

/**
 * [TranslatorMic] over the Sprint 1 [SpeechSession] with the platform recognizer only (plan decision 7): the on-device
 * recognizer when it has the language (works offline, PRD FR-12 AC3), else the system recognizer when online and the
 * user's engine preference allows it. Whisper stays a keyboard fallback this sprint. Main thread only.
 *
 * The screen asks for the microphone permission and shows the voice notice before calling [start]; without the
 * permission [start] only reports [MicState.NoPermission].
 */
class PlatformTranslatorMic(
    private val context: Context,
    private val store: FeatureSettingsStore,
    private val scope: CoroutineScope,
    private val caps: RecognizerCapabilities = PlatformRecognizerCapabilities(context),
    private val clock: () -> Long = System::currentTimeMillis,
) : TranslatorMic {
    private val _state = MutableStateFlow<MicState>(MicState.Idle)
    override val state: StateFlow<MicState> = _state.asStateFlow()

    private val selector = SpeechEngineSelector(caps)
    private var session: SpeechSession? = null
    private var jobs: List<Job> = emptyList()

    override fun start(lang: String, onText: (text: String, final: Boolean) -> Unit) {
        if (session != null) return
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _state.value = MicState.NoPermission
            return
        }
        _state.value = MicState.Listening
        scope.launch {
            val settings = store.current
            // Whisper is not offered here, so a Whisper preference keeps audio on the phone like ON_DEVICE.
            val preference = when (settings.voiceEnginePreference) {
                VoiceEnginePreference.WHISPER -> VoiceEnginePreference.ON_DEVICE
                else -> settings.voiceEnginePreference
            }
            val kind = when (val choice = selector.choose(lang, preference, isOnline(context))) {
                is EngineChoice.Use -> choice.kind.takeIf { it != EngineKind.WHISPER }
                is EngineChoice.Unavailable -> null
            }
            if (kind == null || _state.value != MicState.Listening) {
                if (_state.value == MicState.Listening) _state.value = MicState.Unavailable
                return@launch
            }
            val s = SpeechSession(
                engineFactory = { PlatformSpeechEngine(context, onDevice = kind == EngineKind.SYSTEM_ON_DEVICE) },
                scope = scope,
                timings = VoiceTimings(silenceMs = settings.voiceSilenceMs.toLong()),
                punctuation = SpokenPunctuation,
                clock = clock,
                punctuationEnabled = settings.spokenPunctuation,
            )
            session = s
            jobs = listOf(
                scope.launch {
                    s.output.collect { out ->
                        when (out) {
                            is VoiceOutput.Composing -> onText(out.text, false)
                            is VoiceOutput.Commit -> onText(out.text.trimEnd(), true)
                            VoiceOutput.ClearComposing -> onText("", false)
                        }
                    }
                },
                scope.launch {
                    s.state.collect { v ->
                        when (v) {
                            is VoiceState.Error -> _state.value = MicState.Error
                            VoiceState.Idle -> if (_state.value == MicState.Listening) release()
                            else -> Unit
                        }
                    }
                },
            )
            s.start(lang, continuous = false)
        }
    }

    override fun stop() {
        val s = session
        if (s == null) {
            // Still choosing an engine: nothing records yet.
            if (_state.value == MicState.Listening) _state.value = MicState.Idle
            return
        }
        s.stopByUser()
    }

    override fun destroy() {
        session?.cancel()
        release()
    }

    private fun release() {
        // Let the output collector deliver the last commit before it is cancelled.
        val old = jobs
        jobs = emptyList()
        session = null
        if (_state.value == MicState.Listening) _state.value = MicState.Idle
        scope.launch { old.forEach { it.cancel() } }
    }
}
