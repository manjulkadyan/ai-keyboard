/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.voiceui

import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.EndReason
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.analytics.UnavailableReason
import com.manjul.aikeyboard.core.analytics.VoiceEngine
import com.manjul.aikeyboard.core.voice.EngineKind
import com.manjul.aikeyboard.core.voice.SpeechEngine
import com.manjul.aikeyboard.core.voice.SpeechSession
import com.manjul.aikeyboard.core.voice.VoiceErrorKind
import com.manjul.aikeyboard.core.voice.VoiceOutput
import com.manjul.aikeyboard.core.voice.VoiceState
import com.manjul.aikeyboard.core.voice.platform.AudioFocusHolder
import com.manjul.aikeyboard.core.voice.platform.EngineChoice
import com.manjul.aikeyboard.core.voice.platform.SpeechEngineSelector
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Read when a dictation starts. [lang] is the BCP-47 tag of the active subtype. */
data class VoiceRequest(val lang: String, val preferWhisper: Boolean, val online: Boolean)

/** The engines this build can run. WHISPER is not supported until its engine exists. */
interface VoiceEngines {
    fun supports(kind: EngineKind): Boolean
    fun create(kind: EngineKind, segmented: Boolean): SpeechEngine
}

/** Persisted "first_dictation was logged" flag. */
interface FirstDictationFlag {
    val logged: Boolean
    fun markLogged()
}

/**
 * Voice typing in the keyboard: mic key, permission chip and the session lifecycle. Main thread only.
 *
 * Start order: policy decision, RECORD_AUDIO (both re-read on every [toggle]), engine selection, audio focus,
 * session. Session output goes to [span] only while [generation] still has the value it had at the start; a field
 * change cancels the session ([cancel]) and nothing more reaches the host. Transcript text is never logged.
 */
class VoiceController(
    private val scope: CoroutineScope,
    private val sessionFactory: (engineFactory: () -> SpeechEngine) -> SpeechSession,
    private val engines: VoiceEngines,
    private val selector: SpeechEngineSelector,
    private val request: () -> VoiceRequest,
    private val permission: () -> Boolean,
    private val decision: () -> PolicyDecision,
    private val generation: () -> Int,
    private val focus: AudioFocusHolder,
    private val span: ComposingSpanController,
    private val analytics: (AnalyticsEvent) -> Unit,
    private val firstDictation: FirstDictationFlag,
    private val launchPermission: () -> Unit,
    private val clock: () -> Long,
    private val downloadSystemModel: ((lang: String) -> Unit)? = null,
) {
    private val _ui = MutableStateFlow<VoiceUiState>(VoiceUiState.Hidden)
    val ui: StateFlow<VoiceUiState> = _ui.asStateFlow()

    private inner class Active(val session: SpeechSession, val token: Int, val kind: EngineKind, val lang: String) {
        var words = 0
        var ended = false
        var cancelled = false
        var outputJob: Job? = null
        var stateJob: Job? = null
    }

    private var startJob: Job? = null
    private var active: Active? = null

    /** An ended session whose output may still be draining (its final commit is queued before it goes idle). */
    private var draining: Active? = null
    private var lastLang: String? = null
    private var firstDictationLogged = false
    private val segmentedOff = mutableSetOf<String>()

    fun toggle() {
        active?.let {
            it.session.stopByUser()
            return
        }
        if (startJob?.isActive == true) return
        if (!decision().allowVoice) return
        if (!permission()) {
            _ui.value = VoiceUiState.NeedsPermission
            return
        }
        val token = generation()
        _ui.value = VoiceUiState.Listening(0f)
        startJob = scope.launch {
            val req = request()
            lastLang = req.lang
            val choice = selector.choose(req.lang, req.preferWhisper, req.online)
            if (generation() != token || !decision().allowVoice) {
                _ui.value = VoiceUiState.Hidden
                return@launch
            }
            val kind = when (choice) {
                is EngineChoice.Unavailable -> {
                    unavailable(choice.reason, choice.offerSystemDownload, choice.offerWhisperDownload)
                    return@launch
                }
                is EngineChoice.Use -> choice.kind
            }
            if (!engines.supports(kind)) {
                unavailable(REASON_OFFLINE_NO_MODEL, offerSystem = false, offerWhisper = false)
                return@launch
            }
            if (!focus.acquire()) {
                _ui.value = VoiceUiState.Message(MessageKind.MIC_BUSY)
                return@launch
            }
            begin(kind, req.lang, token)
        }
    }

    /** The permission chip was tapped. The only path that opens the permission screen. */
    fun requestPermission() {
        if (_ui.value != VoiceUiState.NeedsPermission) return
        _ui.value = VoiceUiState.Hidden
        launchPermission()
    }

    /** Back or the close button: stop listening (keeping what was heard) or hide the message. */
    fun dismiss() {
        val a = active
        if (a != null) {
            a.session.stopByUser()
            return
        }
        startJob?.cancel()
        _ui.value = VoiceUiState.Hidden
    }

    fun downloadSystemModel() {
        val lang = lastLang ?: return
        downloadSystemModel?.invoke(lang)
        _ui.value = VoiceUiState.Hidden
    }

    val canDownloadSystemModel: Boolean get() = downloadSystemModel != null

    /** Field change, keyboard hidden or a more restrictive field: remove our partial, commit nothing more. */
    fun cancel(reason: String) {
        startJob?.cancel()
        startJob = null
        stopDraining()
        val a = active
        if (a != null) {
            a.outputJob?.cancel()
            a.stateJob?.cancel()
            a.session.cancel()
            end(a, error = null, reason = EndReason.entries.firstOrNull { it.value == reason } ?: EndReason.FIELD_CHANGE)
        }
        span.discard()
        focus.release()
        _ui.value = VoiceUiState.Hidden
    }

    fun destroy() {
        cancel(EndReason.FIELD_CHANGE.value)
    }

    private fun stopDraining() {
        draining?.let {
            it.outputJob?.cancel()
            it.stateJob?.cancel()
        }
        draining = null
    }

    private fun begin(kind: EngineKind, lang: String, token: Int) {
        stopDraining()
        span.startSession()
        val segmented = segKey(kind, lang) !in segmentedOff
        val session = sessionFactory { engines.create(kind, segmented) }
        val a = Active(session, token, kind, lang)
        active = a
        a.outputJob = scope.launch { session.output.collect { onOutput(a, it) } }
        a.stateJob = scope.launch { session.state.collect { onState(a, it) } }
        analytics(EventCatalog.voiceSessionStart(lang, kind.toAnalytics()))
        session.start(lang, continuous = true)
    }

    private fun onOutput(a: Active, out: VoiceOutput) {
        if (generation() != a.token) {
            // The field changed under us: never write into the new one.
            if (active === a) cancel(EndReason.FIELD_CHANGE.value) else a.outputJob?.cancel()
            return
        }
        when (out) {
            is VoiceOutput.Composing -> span.update(out.text)
            is VoiceOutput.Commit -> {
                span.commit(out.text)
                a.words += countWords(out.text)
            }
            VoiceOutput.ClearComposing -> span.drop()
        }
    }

    private fun onState(a: Active, s: VoiceState) {
        when (s) {
            // The session queues its last output before it goes idle, and that output may not be collected yet.
            // Ending from a new coroutine lets the output collector (already resumed) run first, so the word count
            // includes the final commit.
            VoiceState.Idle -> if (a.session.endReason != null) scope.launch { end(a, error = null, reason = null) }
            is VoiceState.Error -> scope.launch { end(a, error = s.kind, reason = null) }
            VoiceState.Starting -> if (!a.ended) _ui.value = VoiceUiState.Listening(0f)
            is VoiceState.Listening -> if (!a.ended) _ui.value = VoiceUiState.Listening(s.rmsDb)
            VoiceState.Transcribing, VoiceState.Finalizing -> if (!a.ended) _ui.value = VoiceUiState.Transcribing
        }
    }

    private fun end(a: Active, error: VoiceErrorKind?, reason: EndReason?) {
        // A cancelled session shows nothing more, not even an error that was already on its way.
        if (a.cancelled) return
        if (reason != null) a.cancelled = true
        if (!a.ended) {
            a.ended = true
            if (active === a) active = null
            // An error state can still follow the idle state, and the last commit can still be queued.
            if (reason == null) draining = a
            focus.release()
            if (!a.session.isSegmentedSupported(a.kind, a.lang)) segmentedOff += segKey(a.kind, a.lang)
            val endReason = reason ?: EndReason.entries.firstOrNull { it.value == a.session.endReason } ?: EndReason.ERROR
            val errorCode = if (endReason == EndReason.ERROR) a.session.lastErrorCode else null
            analytics(EventCatalog.voiceSessionEnd(clock() - a.session.startedAtMs, a.words, endReason, errorCode))
            if (a.words > 0) logFirstDictation(a.lang)
            _ui.value = if (a.session.tapToResume.value) VoiceUiState.TapToResume else VoiceUiState.Hidden
        }
        if (error != null && reason == null) _ui.value = VoiceUiState.Message(error.toMessageKind())
    }

    private fun logFirstDictation(lang: String) {
        if (firstDictationLogged || firstDictation.logged) return
        firstDictationLogged = true
        firstDictation.markLogged()
        analytics(EventCatalog.firstDictation(lang))
    }

    private fun unavailable(reason: String, offerSystem: Boolean, offerWhisper: Boolean) {
        val r = UnavailableReason.entries.firstOrNull { it.value == reason } ?: UnavailableReason.NO_SERVICE
        analytics(EventCatalog.voiceUnavailable(r))
        val kind = when (r) {
            UnavailableReason.NO_SERVICE -> MessageKind.NO_SERVICE
            UnavailableReason.LANG_UNSUPPORTED -> MessageKind.LANG_UNSUPPORTED
            UnavailableReason.OFFLINE_NO_MODEL -> MessageKind.OFFLINE_NO_MODEL
        }
        _ui.value = VoiceUiState.Message(kind, offerSystemDownload = offerSystem, offerWhisperDownload = offerWhisper)
    }

    private fun segKey(kind: EngineKind, lang: String) = "${kind.analytics}|$lang"

    private companion object {
        const val REASON_OFFLINE_NO_MODEL = "offline_no_model"

        fun countWords(text: String): Int = text.split(' ', '\n', '\t').count { w -> w.any { it.isLetterOrDigit() } }

        fun EngineKind.toAnalytics(): VoiceEngine = when (this) {
            EngineKind.SYSTEM_ON_DEVICE -> VoiceEngine.SYSTEM_ON_DEVICE
            EngineKind.SYSTEM_DEFAULT -> VoiceEngine.SYSTEM_DEFAULT
            EngineKind.WHISPER -> VoiceEngine.WHISPER
        }

        fun VoiceErrorKind.toMessageKind(): MessageKind = when (this) {
            VoiceErrorKind.NO_PERMISSION -> MessageKind.NO_PERMISSION
            VoiceErrorKind.NETWORK -> MessageKind.NETWORK
            VoiceErrorKind.LANGUAGE_UNAVAILABLE -> MessageKind.LANG_UNSUPPORTED
            VoiceErrorKind.BUSY -> MessageKind.MIC_BUSY
            VoiceErrorKind.NO_ENGINE -> MessageKind.NO_SERVICE
            VoiceErrorKind.OTHER -> MessageKind.ERROR
        }
    }
}
