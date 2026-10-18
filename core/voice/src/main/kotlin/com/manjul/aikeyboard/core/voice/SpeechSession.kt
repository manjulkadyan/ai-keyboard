/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Speech session state machine and error policy. All calls and engine callbacks must happen on the thread
 * [scope] runs on (the main thread in the IME). Transcript text is never logged.
 *
 * [endReason] values match the analytics EndReason values: "user", "silence", "error", "cap", "field_change".
 */
class SpeechSession(
    private val engineFactory: () -> SpeechEngine,
    private val scope: CoroutineScope,
    private val timings: VoiceTimings,
    private val punctuation: SpokenPunctuation,
    private val clock: () -> Long,
) {
    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val _output = MutableSharedFlow<VoiceOutput>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val output: Flow<VoiceOutput> = _output

    private val _tapToResume = MutableStateFlow(false)

    /** True after repeated no-speech restarts stopped the session; the UI offers "tap to resume". */
    val tapToResume: StateFlow<Boolean> = _tapToResume.asStateFlow()

    var endReason: String? = null
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
    private var pendingPartial: String? = null
    private var sawSegment = false
    private var restarts = 0
    private var busyRetries = 0

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
        startedAtMs = clock()
        pendingPartial = null
        restarts = 0
        busyRetries = 0
        _tapToResume.value = false
        _state.value = VoiceState.Starting
        capJob = scope.launch {
            delay(timings.sessionCapMs)
            finish("cap", commitPending = true)
        }
        startEngine()
    }

    fun stopByUser() {
        if (!active || stopping) return
        stopping = true
        capJob?.cancel()
        silenceJob?.cancel()
        restartJob?.cancel()
        val e = engine
        if (e == null) {
            // Between restarts: nothing is recording.
            finish("user", commitPending = true)
            return
        }
        _state.value = if (e.supportsPartials) VoiceState.Finalizing else VoiceState.Transcribing
        e.stop()
        if (e.supportsPartials) {
            finalizeJob = scope.launch {
                delay(FINALIZE_TIMEOUT_MS)
                finish("user", commitPending = true)
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
            _output.tryEmit(VoiceOutput.ClearComposing)
        }
        _state.value = VoiceState.Idle
    }

    private fun fail(kind: VoiceErrorKind) {
        finish("error", commitPending = false, clearComposing = true)
        _state.value = VoiceState.Error(kind)
    }

    private fun commit(raw: String) {
        val text = punctuation.apply(raw, lang).trim(' ', '\t')
        if (text.isEmpty()) {
            _output.tryEmit(VoiceOutput.ClearComposing)
            return
        }
        _output.tryEmit(VoiceOutput.Commit(if (text.endsWith("\n")) text else "$text "))
    }

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
        }

        override fun onPartial(text: String) {
            if (!current()) return
            silenceJob?.cancel()
            pendingPartial = text
            _output.tryEmit(VoiceOutput.Composing(punctuation.apply(text, lang)))
        }

        override fun onSegment(text: String) {
            if (!current()) return
            silenceJob?.cancel()
            sawSegment = true
            pendingPartial = null
            commit(text)
        }

        override fun onFinal(text: String) {
            if (!current()) return
            val kind = engine?.kind
            if (kind != null && !sawSegment) segmentedSupported[segKey(kind, lang)] = false
            pendingPartial = null
            restarts = 0
            commit(text)
            if (stopping) {
                finish("user", commitPending = false)
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
        silenceJob?.cancel()
        silenceJob = scope.launch {
            delay(timings.silenceMs)
            finish("silence", commitPending = true)
        }
        scheduleRestart(timings.restartDelayMs)
    }

    private fun handleError(code: Int) {
        when (code) {
            ERROR_SPEECH_TIMEOUT, ERROR_NO_MATCH -> {
                if (stopping) {
                    finish("user", commitPending = true)
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
            ERROR_INSUFFICIENT_PERMISSIONS -> fail(VoiceErrorKind.NO_PERMISSION)
            ERROR_NETWORK_TIMEOUT, ERROR_NETWORK, ERROR_SERVER, ERROR_TOO_MANY_REQUESTS, ERROR_SERVER_DISCONNECTED ->
                fail(VoiceErrorKind.NETWORK)
            ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> fail(VoiceErrorKind.LANGUAGE_UNAVAILABLE)
            else -> fail(VoiceErrorKind.OTHER)
        }
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
    }
}
