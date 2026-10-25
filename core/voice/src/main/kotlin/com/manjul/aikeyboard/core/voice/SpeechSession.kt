/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Speech session state machine and error policy. All calls and engine callbacks must happen on the thread
 * [scope] runs on (the main thread in the IME). Transcript text is never logged.
 *
 * [endReason] values match the analytics EndReason values: "user", "silence", "error", "cap", "field_change".
 * A non-continuous session that ends on its own final result reports "silence" (ruling R19).
 *
 * [output] is lossless (unbounded channel, single consumer): items emitted before a collector subscribes are
 * delivered once it does. A [VoiceOutput.Commit] may start with punctuation; the consumer (Task 11) strips the
 * trailing space of the previously committed text in that case, this class does not.
 */
class SpeechSession(
    private val engineFactory: () -> SpeechEngine,
    private val scope: CoroutineScope,
    private val timings: VoiceTimings,
    private val punctuation: SpokenPunctuation,
    private val clock: () -> Long,
    /** The "spoken punctuation" setting: false commits the words as spoken ("comma" stays a word). */
    private val punctuationEnabled: Boolean = true,
) {
    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val channel = Channel<VoiceOutput>(Channel.UNLIMITED)
    val output: Flow<VoiceOutput> = channel.receiveAsFlow()

    private val _tapToResume = MutableStateFlow(false)

    /** True after repeated no-speech restarts stopped the session; the UI offers "tap to resume". */
    val tapToResume: StateFlow<Boolean> = _tapToResume.asStateFlow()

    var endReason: String? = null
        private set

    /** Raw recognizer error code (android.speech.SpeechRecognizer.ERROR_*) last seen since [start]; never text. */
    var lastErrorCode: Int? = null
        private set

    /** Clock reading when the current (or last) session started. */
    var startedAtMs: Long = 0
        private set

    private var active = false
    private var lang = ""
    private var continuous = false
    private var engine: SpeechEngine? = null
    private var generation = 0
    private var stopping = false
    private var stopReason = "user"
    private var pendingPartial: String? = null
    private var sawSegment = false
    private var restarts = 0
    private var busyRetries = 0
    private var reconnected = false

    private var capJob: Job? = null
    private var silenceJob: Job? = null
    private var restartJob: Job? = null
    private var finalizeJob: Job? = null

    private val segmentedSupported = mutableMapOf<String, Boolean>()

    /** False once an engine of [kind] ended a session on onFinal without ever delivering onSegment. */
    fun isSegmentedSupported(kind: EngineKind, lang: String): Boolean = segmentedSupported[segKey(kind, lang)] ?: true

    fun start(lang: String, continuous: Boolean) {
        if (active) return
        this.lang = lang
        this.continuous = continuous
        active = true
        stopping = false
        endReason = null
        lastErrorCode = null
        startedAtMs = clock()
        pendingPartial = null
        restarts = 0
        busyRetries = 0
        reconnected = false
        _tapToResume.value = false
        _state.value = VoiceState.Starting
        capJob = scope.launch {
            delay(timings.sessionCapMs)
            val e = engine
            // A recorder without partials holds the only copy of the audio: transcribe it instead of dropping it.
            if (e != null && !e.supportsPartials && !stopping) beginStop("cap") else finish("cap", commitPending = true)
        }
        startEngine()
    }

    fun stopByUser() {
        if (!active || stopping) return
        beginStop("user")
    }

    private fun beginStop(reason: String) {
        stopping = true
        stopReason = reason
        capJob?.cancel()
        silenceJob?.cancel()
        restartJob?.cancel()
        val e = engine
        if (e == null) {
            // Between restarts: nothing is recording.
            finish(reason, commitPending = true)
            return
        }
        _state.value = if (e.supportsPartials) VoiceState.Finalizing else VoiceState.Transcribing
        e.stop()
        finalizeJob = scope.launch {
            if (e.supportsPartials) {
                delay(FINALIZE_TIMEOUT_MS)
                finish(reason, commitPending = true)
            } else {
                delay(timings.transcribeTimeoutFor(e.kind))
                fail(VoiceErrorKind.OTHER)
            }
        }
    }

    /** Field change, keyboard hidden or sensitive field: drop everything, commit nothing. */
    fun cancel() {
        if (!active) return
        finish("field_change", commitPending = false, clearComposing = true)
    }

    private fun startEngine() {
        val gen = ++generation
        val e = engineFactory()
        engine = e
        sawSegment = false
        e.start(lang, listenerFor(gen))
    }

    private fun destroyEngine() {
        generation++
        engine?.destroy()
        engine = null
    }

    private fun finish(reason: String, commitPending: Boolean, clearComposing: Boolean = false) {
        if (!active) return
        active = false
        capJob?.cancel()
        silenceJob?.cancel()
        restartJob?.cancel()
        finalizeJob?.cancel()
        val pending = pendingPartial
        pendingPartial = null
        destroyEngine()
        endReason = reason
        if (commitPending && pending != null) {
            commit(pending)
        } else if (clearComposing) {
            channel.trySend(VoiceOutput.ClearComposing)
        }
        _state.value = VoiceState.Idle
    }

    private fun fail(kind: VoiceErrorKind) {
        finish("error", commitPending = false, clearComposing = true)
        _state.value = VoiceState.Error(kind)
    }

    private fun commit(raw: String) {
        val text = punctuate(raw).trim(' ', '\t')
        if (text.isEmpty()) {
            channel.trySend(VoiceOutput.ClearComposing)
            return
        }
        channel.trySend(VoiceOutput.Commit(if (text.endsWith("\n")) text else "$text "))
    }

    private fun punctuate(text: String) = if (punctuationEnabled) punctuation.apply(text, lang) else text

    private fun scheduleRestart(delayMs: Long) {
        // Keep what was heard so far instead of dropping it with the dead engine.
        pendingPartial?.let { commit(it) }
        pendingPartial = null
        destroyEngine()
        _state.value = VoiceState.Starting
        restartJob?.cancel()
        restartJob = scope.launch {
            delay(delayMs)
            if (active && !stopping) startEngine()
        }
    }

    private fun listenerFor(gen: Int) = object : SpeechEngine.Listener {
        private fun current() = active && gen == generation

        override fun onReady() {
            if (!current()) return
            busyRetries = 0
            if (!stopping) _state.value = VoiceState.Listening(0f)
        }

        override fun onRms(db: Float) {
            if (!current() || stopping) return
            _state.value = VoiceState.Listening(db)
            // Still hearing speech: measure the silence window from now.
            if (db > SPEECH_RMS_DB && silenceJob?.isActive == true) armSilence()
        }

        override fun onPartial(text: String) {
            if (!current()) return
            silenceJob?.cancel()
            pendingPartial = text
            channel.trySend(VoiceOutput.Composing(punctuate(text)))
        }

        override fun onSegment(text: String) {
            if (!current()) return
            silenceJob?.cancel()
            sawSegment = true
            restarts = 0
            pendingPartial = null
            commit(text)
        }

        override fun onFinal(text: String) {
            if (!current()) return
            val kind = engine?.kind
            if (kind != null && continuous && engine?.supportsPartials == true && !sawSegment && text.isNotBlank()) segmentedSupported[segKey(kind, lang)] = false
            pendingPartial = null
            restarts = 0
            commit(text)
            if (stopping) {
                finish(stopReason, commitPending = false)
            } else if (continuous) {
                restartAfterFinal()
            } else {
                finish("silence", commitPending = false)
            }
        }

        override fun onEndOfSpeech() {
            // No-op: the final result (or an error) that follows drives the state machine.
        }

        override fun onError(code: Int) {
            if (!current()) return
            handleError(code)
        }
    }

    private fun restartAfterFinal() {
        armSilence()
        scheduleRestart(timings.restartDelayMs)
    }

    private fun armSilence() {
        silenceJob?.cancel()
        silenceJob = scope.launch {
            delay(timings.silenceMs)
            finish("silence", commitPending = true)
        }
    }

    private fun handleError(code: Int) {
        lastErrorCode = code
        if (stopping && code != ERROR_SPEECH_TIMEOUT && code != ERROR_NO_MATCH) {
            // Includes BUSY: restarting while stopping would leave the session stuck.
            val e = engine
            if (pendingPartial != null || e?.supportsPartials != false) {
                finish(stopReason, commitPending = true)
            } else {
                fail(errorKind(code)) // Whisper: the recording is lost, tell the user.
            }
            return
        }
        when (code) {
            ERROR_SPEECH_TIMEOUT, ERROR_NO_MATCH -> {
                if (stopping) {
                    finish(stopReason, commitPending = true)
                } else if (restarts >= timings.maxRestarts) {
                    finish("error", commitPending = true)
                    _tapToResume.value = true
                } else {
                    restarts++
                    scheduleRestart(timings.restartDelayMs)
                }
            }
            ERROR_RECOGNIZER_BUSY -> {
                if (busyRetries >= timings.maxBusyRetries) {
                    fail(VoiceErrorKind.BUSY)
                } else {
                    busyRetries++
                    scheduleRestart(timings.busyDelayMs)
                }
            }
            ERROR_SERVER_DISCONNECTED -> {
                // The recognition service went away under us (often torn down as another client unbound it), not
                // a network problem: reconnect once before telling the user.
                if (reconnected) {
                    fail(errorKind(code))
                } else {
                    reconnected = true
                    scheduleRestart(timings.busyDelayMs)
                }
            }
            else -> fail(errorKind(code))
        }
    }

    private fun errorKind(code: Int) = when (code) {
        ERROR_RECOGNIZER_BUSY -> VoiceErrorKind.BUSY
        ERROR_INSUFFICIENT_PERMISSIONS -> VoiceErrorKind.NO_PERMISSION
        ERROR_NETWORK_TIMEOUT, ERROR_NETWORK, ERROR_SERVER, ERROR_TOO_MANY_REQUESTS, ERROR_SERVER_DISCONNECTED ->
            VoiceErrorKind.NETWORK
        ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> VoiceErrorKind.LANGUAGE_UNAVAILABLE
        else -> VoiceErrorKind.OTHER
    }

    private fun segKey(kind: EngineKind, lang: String) = "${kind.analytics}|$lang"

    // android.speech.SpeechRecognizer.ERROR_* values, kept as raw ints so this logic has no Android dependency.
    private companion object {
        const val ERROR_NETWORK_TIMEOUT = 1
        const val ERROR_NETWORK = 2
        const val ERROR_SERVER = 4
        const val ERROR_SPEECH_TIMEOUT = 6
        const val ERROR_NO_MATCH = 7
        const val ERROR_RECOGNIZER_BUSY = 8
        const val ERROR_INSUFFICIENT_PERMISSIONS = 9
        const val ERROR_TOO_MANY_REQUESTS = 10
        const val ERROR_SERVER_DISCONNECTED = 11
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13

        /** Upper bound for waiting on the final result after the user stops a partial-capable engine. */
        const val FINALIZE_TIMEOUT_MS = 3000L

        /** onRms level (dB) above which the microphone is hearing speech; the recognizer noise floor sits near 0. */
        const val SPEECH_RMS_DB = 3f
    }
}
