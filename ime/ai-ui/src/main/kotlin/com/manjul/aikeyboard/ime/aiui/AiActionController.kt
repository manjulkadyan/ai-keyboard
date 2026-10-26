/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.aiui

import com.manjul.aikeyboard.core.ai.AiEngine
import com.manjul.aikeyboard.core.ai.AiError
import com.manjul.aikeyboard.core.ai.AiEvent
import com.manjul.aikeyboard.core.ai.AiException
import com.manjul.aikeyboard.core.ai.AiLru
import com.manjul.aikeyboard.core.ai.AiPauseGate
import com.manjul.aikeyboard.core.ai.AiRequest
import com.manjul.aikeyboard.core.ai.RewriteMode
import com.manjul.aikeyboard.core.analytics.AiAction
import com.manjul.aikeyboard.core.analytics.AiEngineParam
import com.manjul.aikeyboard.core.analytics.AiErrorReason
import com.manjul.aikeyboard.core.analytics.AiFeature
import com.manjul.aikeyboard.core.analytics.AiTone
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.config.ConfigKeys
import com.manjul.aikeyboard.core.config.RemoteConfig
import com.manjul.aikeyboard.ime.editing.CapturedText
import com.manjul.aikeyboard.ime.editing.ReplaceResult
import com.manjul.aikeyboard.ime.editing.SafeReplacer
import com.manjul.aikeyboard.ime.editing.TextCapture
import com.manjul.aikeyboard.ime.editing.TextTarget
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the ✦ AI rewrite sheet (PRD FR-15, TRD §4.4): consent, capture, the streamed preview, Replace / Insert /
 * Copy / Undo, errors, the quota card and Report. Main thread only. Captured text and output are user content: never
 * logged, never in analytics or exceptions.
 *
 * Privacy (PC-1, Review Focus 2): nothing is captured or sent without [hasConsent] and an [isAvailable] decision
 * (`allowAi && allowCloud`, not paused, `ai_enabled_global`). The text is captured at [chooseTone], with the field
 * [generation]. Every streamed event and every host write re-checks the live [decision] and that generation; on a
 * mismatch the stream is cancelled, the field-scoped [lru] cleared, the pending undo dropped and the sheet hidden, so a
 * late delta changes nothing. [onRestricted] and [onFieldChanged] do the same at once.
 *
 * Host text (TRD §4.1.3, Review Focus 1): the preview stays in the sheet. The host changes only on [replace] (through
 * [SafeReplacer], which writes only while the captured range still hashes to what was captured; otherwise
 * [AiSheetState.TextChanged] offers Insert), [insert] (at the cursor, never deleting) and [undo] (within
 * [UNDO_WINDOW_MS], only while our output is still in place).
 *
 * Quota: a retry after an error reuses the request's `client_req_id`, so it counts once; Retry on a finished output
 * is a new action with a new id. A re-tap of the same tone on the same text is served from [lru] with no request.
 *
 * Analytics: `ai_request{engine}` once per engine call (not per LRU hit), `ai_rewrite_done` on Replace and Insert
 * only, `ai_quota_hit`, `ai_error` (including `text_changed`), `ai_output_reported`.
 */
class AiActionController(
    private val engine: AiEngine,
    private val replacer: SafeReplacer,
    private val targetProvider: () -> TextTarget?,
    private val decision: () -> PolicyDecision,
    private val generation: () -> Int,
    private val hasConsent: () -> Boolean,
    private val requestConsent: () -> Unit,
    private val locale: () -> String,
    private val translateTarget: () -> String?,
    private val lru: AiLru,
    private val pause: AiPauseGate,
    private val config: RemoteConfig,
    private val analytics: (AnalyticsEvent) -> Unit,
    private val copyToClipboard: (String) -> Unit,
    private val report: suspend (ReportPayload) -> Unit,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val newRequestId: () -> String = { UUID.randomUUID().toString() },
) {
    /** One captured text and the request for it. [output] is user content. */
    private class Session(val captured: CapturedText, val generation: Int, val request: AiRequest.Rewrite) {
        val mode: RewriteMode get() = request.mode
        var output: String = ""
        var startedAt: Long = 0L
        var latencyMs: Long = 0L
        var finished: Boolean = false

        override fun toString(): String = "Session(mode=$mode, captured=$captured, chars=${output.length})"
    }

    private val _state = MutableStateFlow<AiSheetState>(AiSheetState.Hidden)
    val state: StateFlow<AiSheetState> = _state.asStateFlow()

    private val _isAvailable = MutableStateFlow(computeAvailable())

    /** Whether the ✦ chip may show: `allowAi && allowCloud && !paused && ai_enabled_global`. */
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private var session: Session? = null
    private var streamJob: Job? = null
    private var undoJob: Job? = null
    private var pauseJob: Job? = null

    /** Bumped on every send and every cancel: an event of an older stream is dropped. */
    private var seq = 0L

    /** What [AiSheetState.Reporting] goes back to. */
    private var beforeReport: AiSheetState? = null

    /** The text the current output is for (tests check host writes against its range). */
    internal val captured: CapturedText? get() = session?.captured

    /** The field generation [captured] was taken in. */
    internal val captureGeneration: Int? get() = session?.generation

    /** Re-reads the live decision, pause and config into [isAvailable]; call when the decision changes. */
    fun refreshAvailability() {
        _isAvailable.value = computeAvailable()
    }

    /**
     * The shared [AiPauseGate] was set (here or by ✦ Polish): hide the chip now and show it again when the pause ends.
     */
    fun onPaused() {
        refreshAvailability()
        pauseJob?.cancel()
        pauseJob = scope.launch {
            delay(AiPauseGate.PAUSED_MS)
            refreshAvailability()
        }
    }

    /** The ✦ chip: shows the tones, or asks for consent. No-op while AI is not available. */
    fun open() {
        refreshAvailability()
        if (!_isAvailable.value) return
        close()
        if (!hasConsent()) {
            needConsent()
            return
        }
        _state.value = chooseToneState()
    }

    /** Captures the text and runs [mode] on it (or serves it from the LRU). */
    fun chooseTone(mode: RewriteMode) = start(mode, fresh = false)

    /** Stops the stream: the text so far stays as the output (not cached); nothing is written. */
    fun stop() {
        if (_state.value !is AiSheetState.Streaming) return
        val s = session ?: return
        cancelStream()
        if (s.output.isEmpty()) {
            _state.value = chooseToneState()
        } else {
            s.finished = true
            s.latencyMs = clock() - s.startedAt
            _state.value = AiSheetState.Ready(s.mode, s.output, trimmed = false)
        }
    }

    /**
     * After a retryable error: the same request again, same `client_req_id`. On an output: a new rewrite of the
     * same tone, captured again, bypassing the LRU.
     */
    fun retry() {
        val s = session ?: return
        when (val st = _state.value) {
            is AiSheetState.Error -> {
                if (!st.canRetry) return
                if (!mayStart()) return
                if (!sessionValid(s)) {
                    abort()
                    return
                }
                send(s)
            }
            is AiSheetState.Ready, is AiSheetState.TextChanged -> start(s.mode, fresh = true)
            else -> Unit
        }
    }

    /** Writes the output over the captured range, only while it still holds the captured text. */
    fun replace() {
        if (_state.value !is AiSheetState.Ready) return
        val s = session ?: return
        if (!sessionValid(s)) {
            abort()
            return
        }
        when (replacer.replace(s.captured, s.output)) {
            ReplaceResult.REPLACED -> {
                analytics(EventCatalog.aiRewriteDone(tone(s.mode), s.captured.text.length, s.latencyMs, AiAction.REPLACE))
                _state.value = AiSheetState.Replaced(s.mode)
                startUndoTimer()
            }
            ReplaceResult.TEXT_CHANGED -> {
                analytics(EventCatalog.aiError(AiErrorReason.TEXT_CHANGED))
                _state.value = AiSheetState.TextChanged(s.output)
            }
            ReplaceResult.FAILED -> _state.value = AiSheetState.TextChanged(s.output)
        }
    }

    /** Inserts the output at the cursor (never deletes) and closes the sheet. */
    fun insert() {
        val st = _state.value
        if (st !is AiSheetState.Ready && st !is AiSheetState.TextChanged) return
        val s = session ?: return
        if (!sessionValid(s)) {
            abort()
            return
        }
        if (replacer.insert(s.output)) {
            analytics(EventCatalog.aiRewriteDone(tone(s.mode), s.captured.text.length, s.latencyMs, AiAction.INSERT))
            close()
        }
    }

    /** Copies the output to the clipboard. Not an `ai_rewrite_done` action. */
    fun copy() {
        val st = _state.value
        if (st !is AiSheetState.Ready && st !is AiSheetState.TextChanged) return
        val s = session ?: return
        if (!sessionValid(s)) {
            abort()
            return
        }
        copyToClipboard(s.output)
    }

    /** Within [UNDO_WINDOW_MS] of a replace: puts the original back if our output is still in place. Closes the sheet. */
    fun undo(): Boolean {
        if (_state.value !is AiSheetState.Replaced) return false
        val s = session ?: return false
        if (!sessionValid(s)) {
            abort()
            return false
        }
        val undone = replacer.undo()
        close()
        return undone
    }

    fun openReport() {
        val st = _state.value
        if (st !is AiSheetState.Ready && st !is AiSheetState.TextChanged) return
        val s = session ?: return
        beforeReport = st
        _state.value = AiSheetState.Reporting(s.mode)
    }

    /** Back from the report sheet without sending. */
    fun closeReport() {
        if (_state.value !is AiSheetState.Reporting) return
        _state.value = beforeReport ?: AiSheetState.Hidden
        beforeReport = null
    }

    /** Sends the report; the output goes with it only when [includeText]. A failed send is dropped silently. */
    fun submitReport(reason: ReportReason, includeText: Boolean) {
        if (_state.value !is AiSheetState.Reporting) return
        val s = session ?: return
        val payload = ReportPayload(s.request.clientReqId, PROMPT_REWRITE, reason, if (includeText) s.output else null)
        analytics(EventCatalog.aiOutputReported(tone(s.mode)))
        closeReport()
        if (!decision().allowCloud) return
        scope.launch {
            try {
                report(payload)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Reporting is best effort; the error may carry the text, so it is not kept.
            }
        }
    }

    /** Closes the sheet: cancels the stream and the pending undo. The LRU stays for this field. */
    fun dismiss() = close()

    /** The field changed: cancel, drop the field-scoped LRU and undo, hide. */
    fun onFieldChanged() = abort()

    /** The decision got more restrictive (sensitive field, AI off): cancel, drop the LRU and undo, hide. */
    fun onRestricted() = abort()

    private fun start(mode: RewriteMode, fresh: Boolean) {
        if (!mayStart()) return
        val target = translateTarget()
        if (mode == RewriteMode.TRANSLATE && target == null) return
        close()
        val field = targetProvider()
        val captured = field?.let { TextCapture.capture(it, MAX_INPUT_CHARS) }
        if (captured == null) {
            _state.value = AiSheetState.NothingToRewrite
            return
        }
        if (captured.text.length > MAX_INPUT_CHARS) {
            analytics(EventCatalog.aiError(AiErrorReason.TOO_LONG))
            _state.value = AiSheetState.Error(AiError.TooLong, canRetry = false)
            return
        }
        val request = AiRequest.Rewrite(
            mode = mode,
            locale = locale(),
            text = captured.text,
            targetLang = if (mode == RewriteMode.TRANSLATE) target else null,
            clientReqId = newRequestId(),
        )
        val s = Session(captured, generation(), request)
        session = s
        if (!fresh) {
            val cached = lru.get(AiLru.Key.of(request))
            if (cached != null) {
                s.output = cached
                s.finished = true
                _state.value = AiSheetState.Ready(mode, cached, trimmed = false)
                return
            }
        }
        send(s)
    }

    /** Availability and consent, checked before every send. Asks for consent when it is missing. */
    private fun mayStart(): Boolean {
        refreshAvailability()
        if (!_isAvailable.value) {
            abort()
            return false
        }
        if (!hasConsent()) {
            close()
            needConsent()
            return false
        }
        return true
    }

    private fun send(s: Session) {
        cancelStream()
        val mine = seq
        s.output = ""
        s.finished = false
        s.startedAt = clock()
        _state.value = AiSheetState.Streaming(s.mode, "", stillWorking = false)
        analytics(EventCatalog.aiRequest(if (engine.name == "nano") AiEngineParam.ON_DEVICE else AiEngineParam.CLOUD))
        streamJob = scope.launch {
            try {
                engine.stream(s.request).collect { event ->
                    if (mine != seq || s.finished) throw CancellationException("superseded")
                    if (!sessionValid(s)) {
                        abort()
                        throw CancellationException("restricted")
                    }
                    onEvent(s, event)
                }
                if (mine == seq && !s.finished) onError(AiError.Unavailable)
            } catch (e: AiException) {
                if (mine == seq && !s.finished) onError(e.error)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Never surface a raw exception: it may echo the request.
                if (mine == seq && !s.finished) onError(AiError.Unavailable)
            }
        }
    }

    private fun onEvent(s: Session, event: AiEvent) {
        when (event) {
            is AiEvent.Delta -> {
                s.output += event.text
                val still = (_state.value as? AiSheetState.Streaming)?.stillWorking ?: false
                _state.value = AiSheetState.Streaming(s.mode, s.output, still)
            }
            AiEvent.StillWorking -> _state.value = AiSheetState.Streaming(s.mode, s.output, stillWorking = true)
            is AiEvent.Done -> {
                s.finished = true
                if (s.output.isEmpty()) {
                    onError(AiError.Unavailable)
                    return
                }
                s.latencyMs = clock() - s.startedAt
                lru.put(AiLru.Key.of(s.request), s.output)
                _state.value = AiSheetState.Ready(s.mode, s.output, event.trimmed)
            }
        }
    }

    private fun onError(error: AiError) {
        if (error is AiError.Quota) {
            analytics(EventCatalog.aiQuotaHit(AiFeature.REWRITE))
        } else {
            analytics(EventCatalog.aiError(reason(error)))
        }
        if (error == AiError.Paused) {
            pause.pauseFor(AiPauseGate.PAUSED_MS)
            onPaused()
        }
        _state.value = AiSheetState.Error(error, canRetry(error))
    }

    private fun startUndoTimer() {
        undoJob?.cancel()
        undoJob = scope.launch {
            delay(UNDO_WINDOW_MS)
            if (_state.value is AiSheetState.Replaced) close()
        }
    }

    private fun needConsent() {
        _state.value = AiSheetState.NeedsConsent
        requestConsent()
    }

    /** Hides the sheet: cancels the stream and the undo, forgets the session. Keeps the LRU. */
    private fun close() {
        cancelStream()
        undoJob?.cancel()
        undoJob = null
        replacer.invalidate()
        session = null
        beforeReport = null
        _state.value = AiSheetState.Hidden
    }

    /** [close], plus drop the field-scoped LRU and re-check availability. */
    private fun abort() {
        close()
        lru.clear()
        refreshAvailability()
    }

    private fun cancelStream() {
        seq++
        streamJob?.cancel()
        streamJob = null
    }

    private fun sessionValid(s: Session): Boolean {
        val d = decision()
        return d.allowAi && d.allowCloud && generation() == s.generation
    }

    private fun computeAvailable(): Boolean {
        val d = decision()
        return d.allowAi && d.allowCloud && !pause.isPaused && config.bool(ConfigKeys.AI_ENABLED_GLOBAL)
    }

    private fun chooseToneState(): AiSheetState.ChooseTone {
        val target = translateTarget()
        val tones = RewriteMode.entries.filter { it != RewriteMode.TRANSLATE || target != null }
        return AiSheetState.ChooseTone(tones, target)
    }

    companion object {
        /** Client-side input cap (TRD §11.4). */
        const val MAX_INPUT_CHARS = TextCapture.DEFAULT_MAX_CHARS

        /** How long Undo is offered after Replace (PRD FR-15 AC1). */
        const val UNDO_WINDOW_MS = 5_000L

        /** The kb-api prompt `/v1/rewrite` runs, for `/v1/ai/report`. */
        const val PROMPT_REWRITE = "rewrite@1"

        internal fun tone(mode: RewriteMode): AiTone = AiTone.valueOf(mode.name)

        internal fun canRetry(error: AiError): Boolean = when (error) {
            AiError.Offline, AiError.Timeout, AiError.Unavailable, is AiError.RateLimited -> true
            AiError.Refused, AiError.Paused, AiError.TooLong, AiError.NotConfigured, is AiError.Quota -> false
        }

        internal fun reason(error: AiError): AiErrorReason = when (error) {
            AiError.Offline -> AiErrorReason.OFFLINE
            AiError.Timeout -> AiErrorReason.TIMEOUT
            is AiError.RateLimited -> AiErrorReason.RATE_LIMITED
            AiError.Refused -> AiErrorReason.REFUSED
            AiError.Paused -> AiErrorReason.PAUSED
            AiError.TooLong -> AiErrorReason.TOO_LONG
            AiError.Unavailable, AiError.NotConfigured, is AiError.Quota -> AiErrorReason.UNAVAILABLE
        }
    }
}
