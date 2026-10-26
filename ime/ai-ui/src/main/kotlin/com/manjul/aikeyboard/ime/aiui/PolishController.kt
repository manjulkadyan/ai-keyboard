/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.aiui

import com.manjul.aikeyboard.core.ai.AiEngine
import com.manjul.aikeyboard.core.ai.AiError
import com.manjul.aikeyboard.core.ai.AiEvent
import com.manjul.aikeyboard.core.ai.AiException
import com.manjul.aikeyboard.core.ai.AiPauseGate
import com.manjul.aikeyboard.core.ai.AiRequest
import com.manjul.aikeyboard.core.ai.OutputScript
import com.manjul.aikeyboard.core.ai.RewriteMode
import com.manjul.aikeyboard.core.analytics.AiEngineParam
import com.manjul.aikeyboard.core.analytics.AiErrorReason
import com.manjul.aikeyboard.core.analytics.AiFeature
import com.manjul.aikeyboard.core.analytics.AiTone
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.analytics.PolishMode
import com.manjul.aikeyboard.core.config.ConfigKeys
import com.manjul.aikeyboard.core.config.RemoteConfig
import com.manjul.aikeyboard.ime.editing.CapturedText
import com.manjul.aikeyboard.ime.editing.DictatedSpan
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
 * Post-dictation ✦ Polish (PRD FR-16, plan decisions 11-13). Main thread only. The dictated text and the output are
 * user content: never logged, never in analytics or exceptions.
 *
 * After [onDictationFinished] the [chip] shows for [CHIP_MS], until the cursor leaves the span or the user types
 * ([onCursorOrTyping]), or until the field changes. It shows only while AI is available (`allowAi && allowCloud`, not
 * paused, `ai_enabled_global`). [polish] sends the span to `/v1/polish` (manual Polish needs `consent_ai`) and streams
 * the preview into the sheet ([state], the [AiSheetState]s of the rewrite sheet with a [PolishMode]). The host changes
 * only on [replace], which swaps exactly the span through [SafeReplacer] (hash-checked; [AiSheetState.TextChanged] and
 * nothing written when the span was edited), or [insert] (at the cursor, never deleting). [undo] puts the raw
 * transcript back within [UNDO_WINDOW_MS] while our output is still in place.
 *
 * Auto-polish ([AutoPolishPolicy], plus `consent_ai`) runs without a tap and without a sheet, and replaces the span
 * itself when the output arrives (Undo is then offered as after a manual Replace). Moving out of the span or typing
 * cancels it. A [AiError.Quota] during auto-polish keeps the raw text, shows nothing and pauses auto-polish until the
 * next UTC day ([quotaPaused]); any other auto error shows the manual chip again.
 *
 * Like [AiActionController], every streamed event and every host write re-checks the live [decision] and the field
 * [generation] the span was recorded in; on a mismatch the stream is cancelled and the chip and sheet hidden.
 *
 * Analytics: `ai_request{engine}` per call, `ai_polish_done{mode, auto}` when the output is applied (Replace, Insert
 * or auto replace), `ai_quota_hit{feature=polish}`, `ai_error`, `ai_output_reported{tone=polish}`.
 */
class PolishController(
    private val engine: AiEngine,
    private val replacer: SafeReplacer,
    private val targetProvider: () -> TextTarget?,
    private val decision: () -> PolicyDecision,
    private val generation: () -> Int,
    private val hasConsent: () -> Boolean,
    private val requestConsent: () -> Unit,
    private val translateTarget: () -> String?,
    private val pause: AiPauseGate,
    private val config: RemoteConfig,
    private val analytics: (AnalyticsEvent) -> Unit,
    private val copyToClipboard: (String) -> Unit,
    private val report: suspend (ReportPayload) -> Unit,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val autoPolish: () -> Boolean,
    private val autoPolishConsentAt: () -> Long,
    private val isPro: () -> Boolean,
    private val outputScript: () -> OutputScript,
    private val newRequestId: () -> String = { UUID.randomUUID().toString() },
) {
    private class Session(
        val span: DictatedSpan,
        val captured: CapturedText,
        val generation: Int,
        val request: AiRequest.Polish,
        val translate: Boolean,
        val auto: Boolean,
    ) {
        val mode: PolishMode get() = if (translate) PolishMode.CLEAN_TRANSLATE else PolishMode.CLEAN
        val raw = StringBuilder()
        var output: String = ""
        var preview: String = ""
        var startedAt: Long = 0L
        var finished: Boolean = false

        override fun toString(): String = "Session(mode=$mode, auto=$auto, captured=$captured, chars=${output.length})"
    }

    private val _chip = MutableStateFlow<PolishChipState?>(null)

    /** The ✦ Polish chip, or null when it is hidden. */
    val chip: StateFlow<PolishChipState?> = _chip.asStateFlow()

    private val _state = MutableStateFlow<AiSheetState>(AiSheetState.Hidden)

    /** The Polish sheet. Stays [AiSheetState.Hidden] while auto-polish runs. */
    val state: StateFlow<AiSheetState> = _state.asStateFlow()

    /** The span the chip offers, and the field generation it was dictated in. */
    private var span: DictatedSpan? = null
    private var spanGeneration = 0

    /** The last cursor position that still counts as "at the span": its end, or after the one space dictation left. */
    private var reach = 0

    private var session: Session? = null
    private var chipJob: Job? = null
    private var streamJob: Job? = null
    private var undoJob: Job? = null
    private var seq = 0L
    private var beforeReport: AiSheetState? = null
    private var quotaPausedUntil = Long.MIN_VALUE

    /** Auto-polish hit the quota and is paused until the next UTC day. Manual Polish is still offered. */
    val quotaPaused: Boolean get() = clock() < quotaPausedUntil

    /** A dictation committed [span]: auto-polish it, or offer the chip. Ends any earlier Polish. */
    fun onDictationFinished(span: DictatedSpan) {
        close()
        hideChip()
        if (!isAvailable()) return
        val end = span.range.last + 1
        reach = targetProvider()?.selection()?.first?.takeIf { it == end + 1 } ?: end
        val auto = hasConsent() &&
            AutoPolishPolicy.shouldAutoRun(autoPolish(), autoPolishConsentAt(), isPro(), decision(), quotaPaused)
        if (auto) {
            start(span, generation(), translate = false, auto = true)
        } else {
            showChip(span, generation())
        }
    }

    /** The chip: "Clean up" ([translate] false) or "Clean up + translate". Asks for `consent_ai` when it is missing. */
    fun polish(translate: Boolean) {
        val sp = span ?: return
        if (_chip.value == null) return
        if (!isAvailable() || generation() != spanGeneration) {
            abort()
            return
        }
        if (translate && translateTarget() == null) return
        if (!hasConsent()) {
            close()
            _state.value = AiSheetState.NeedsConsent
            requestConsent()
            return
        }
        start(sp, spanGeneration, translate, auto = false)
    }

    /** The cursor moved to [cursor] (or the user typed): outside the span the chip goes, and auto-polish stops. */
    fun onCursorOrTyping(cursor: Int) {
        val sp = span
        if (sp != null && _chip.value != null && !near(sp, cursor)) hideChip()
        val s = session ?: return
        if (s.auto && !s.finished && !near(s.span, cursor)) close()
    }

    /** Hides the chip without polishing. */
    fun dismissChip() = hideChip()

    /** Stops the stream: the text so far stays as the output; nothing is written. */
    fun stop() {
        if (_state.value !is AiSheetState.Streaming) return
        val s = session ?: return
        cancelStream()
        if (s.preview.isEmpty()) {
            close()
        } else {
            s.finished = true
            s.output = s.preview
            _state.value = AiSheetState.Ready(RewriteMode.FIX, s.output, trimmed = false, polish = s.mode)
        }
    }

    /** After a retryable error: the same request (same `client_req_id`). On an output: a new request. */
    fun retry() {
        val s = session ?: return
        when (val st = _state.value) {
            is AiSheetState.Error -> {
                if (!st.canRetry) return
                if (!isAvailable() || !sessionValid(s)) {
                    abort()
                    return
                }
                send(s)
            }
            is AiSheetState.Ready -> start(s.span, s.generation, s.translate, auto = false)
            else -> Unit
        }
    }

    /** Swaps the dictated span for the output, only while the span still holds the raw transcript. */
    fun replace() {
        if (_state.value !is AiSheetState.Ready) return
        val s = session ?: return
        if (!sessionValid(s)) {
            abort()
            return
        }
        when (replacer.replace(s.captured, s.output)) {
            ReplaceResult.REPLACED -> applied(s)
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
            analytics(EventCatalog.aiPolishDone(s.mode, auto = false))
            close()
        }
    }

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

    /** Within [UNDO_WINDOW_MS] of a replace: puts the raw transcript back if our output is still in place. */
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
        _state.value = AiSheetState.Reporting(RewriteMode.FIX, s.mode)
    }

    fun closeReport() {
        if (_state.value !is AiSheetState.Reporting) return
        _state.value = beforeReport ?: AiSheetState.Hidden
        beforeReport = null
    }

    /** Sends the report; the output goes with it only when [includeText]. Best effort. */
    fun submitReport(reason: ReportReason, includeText: Boolean) {
        if (_state.value !is AiSheetState.Reporting) return
        val s = session ?: return
        val payload = ReportPayload(s.request.clientReqId, PROMPT_POLISH, reason, if (includeText) s.output else null)
        analytics(EventCatalog.aiOutputReported(AiTone.POLISH))
        closeReport()
        if (!decision().allowCloud) return
        scope.launch {
            try {
                report(payload)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best effort; the error may carry the text, so it is not kept.
            }
        }
    }

    /** Closes the sheet (cancels the stream and the pending undo). */
    fun dismiss() = close()

    /** The field changed: the span belongs to the old field. */
    fun onFieldChanged() = abort()

    /** The decision got more restrictive: cancel and hide everything. */
    fun onRestricted() = abort()

    private fun start(sp: DictatedSpan, gen: Int, translate: Boolean, auto: Boolean) {
        close()
        hideChip()
        // Never send text that is no longer the dictation.
        if (targetProvider()?.textInRange(sp.range) != sp.text) return
        if (sp.text.length > MAX_INPUT_CHARS) {
            if (!auto) {
                analytics(EventCatalog.aiError(AiErrorReason.TOO_LONG))
                _state.value = AiSheetState.Error(AiError.TooLong, canRetry = false)
            }
            return
        }
        val target = if (translate) translateTarget() ?: return else sp.lang
        val request = AiRequest.Polish(
            transcript = sp.text,
            sourceLang = sp.lang,
            targetLang = target,
            script = outputScript(),
            clientReqId = newRequestId(),
        )
        val s = Session(sp, TextCapture.ofSpan(sp.range, sp.text), gen, request, translate, auto)
        session = s
        send(s)
    }

    private fun send(s: Session) {
        cancelStream()
        val mine = seq
        s.raw.setLength(0)
        s.preview = ""
        s.output = ""
        s.finished = false
        s.startedAt = clock()
        if (!s.auto) _state.value = AiSheetState.Streaming(RewriteMode.FIX, "", stillWorking = false, polish = s.mode)
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
                if (mine == seq && !s.finished) onError(s, AiError.Unavailable)
            } catch (e: AiException) {
                if (mine == seq && !s.finished) onError(s, e.error)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Never surface a raw exception: it may echo the request.
                if (mine == seq && !s.finished) onError(s, AiError.Unavailable)
            }
        }
    }

    private fun onEvent(s: Session, event: AiEvent) {
        when (event) {
            is AiEvent.Delta -> {
                s.raw.append(event.text)
                s.preview = PolishJson.partialString(s.raw, if (s.translate) "translation" else "polished").orEmpty()
                if (!s.auto) {
                    val still = (_state.value as? AiSheetState.Streaming)?.stillWorking ?: false
                    _state.value = AiSheetState.Streaming(RewriteMode.FIX, s.preview, still, s.mode)
                }
            }
            AiEvent.StillWorking -> if (!s.auto) {
                _state.value = AiSheetState.Streaming(RewriteMode.FIX, s.preview, stillWorking = true, polish = s.mode)
            }
            is AiEvent.Done -> {
                s.finished = true
                val output = (if (s.translate) event.translation else event.polished)?.takeIf { it.isNotBlank() }
                if (output == null) {
                    onError(s, AiError.Unavailable)
                    return
                }
                s.output = output
                if (s.auto) {
                    autoReplace(s)
                } else {
                    _state.value = AiSheetState.Ready(RewriteMode.FIX, output, event.trimmed, s.mode)
                }
            }
        }
    }

    private fun autoReplace(s: Session) {
        when (replacer.replace(s.captured, s.output)) {
            ReplaceResult.REPLACED -> applied(s)
            ReplaceResult.TEXT_CHANGED -> {
                analytics(EventCatalog.aiError(AiErrorReason.TEXT_CHANGED))
                close()
            }
            ReplaceResult.FAILED -> close()
        }
    }

    private fun applied(s: Session) {
        analytics(EventCatalog.aiPolishDone(s.mode, s.auto))
        _state.value = AiSheetState.Replaced(RewriteMode.FIX, s.mode)
        undoJob?.cancel()
        undoJob = scope.launch {
            delay(UNDO_WINDOW_MS)
            if (_state.value is AiSheetState.Replaced) close()
        }
    }

    private fun onError(s: Session, error: AiError) {
        if (error is AiError.Quota) {
            analytics(EventCatalog.aiQuotaHit(AiFeature.POLISH))
            quotaPausedUntil = nextUtcDay(clock())
        } else {
            analytics(EventCatalog.aiError(AiActionController.reason(error)))
        }
        if (error == AiError.Paused) pause.pauseFor(AiPauseGate.PAUSED_MS)
        if (s.auto) {
            // Auto-polish fails quietly: the raw transcript stays. Outside a quota stop the manual chip comes back.
            close()
            if (error !is AiError.Quota && isAvailable() && generation() == s.generation) showChip(s.span, s.generation)
            return
        }
        _state.value = AiSheetState.Error(error, AiActionController.canRetry(error))
    }

    /** The cursor is at the span: inside it, at its end, or after the space the dictation left. Typing moves past. */
    private fun near(sp: DictatedSpan, cursor: Int): Boolean = cursor in sp.range.first..reach

    private fun showChip(sp: DictatedSpan, gen: Int) {
        span = sp
        spanGeneration = gen
        val target = translateTarget()
        _chip.value = PolishChipState(canTranslate = target != null, targetLang = target)
        chipJob?.cancel()
        chipJob = scope.launch {
            delay(CHIP_MS)
            hideChip()
        }
    }

    private fun hideChip() {
        chipJob?.cancel()
        chipJob = null
        span = null
        _chip.value = null
    }

    private fun close() {
        cancelStream()
        undoJob?.cancel()
        undoJob = null
        replacer.invalidate()
        session = null
        beforeReport = null
        _state.value = AiSheetState.Hidden
    }

    private fun abort() {
        close()
        hideChip()
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

    private fun isAvailable(): Boolean {
        val d = decision()
        return d.allowAi && d.allowCloud && !pause.isPaused && config.bool(ConfigKeys.AI_ENABLED_GLOBAL)
    }

    companion object {
        /** How long the chip is offered after a dictation (plan decision 11). */
        const val CHIP_MS = 30_000L

        const val UNDO_WINDOW_MS = AiActionController.UNDO_WINDOW_MS

        const val MAX_INPUT_CHARS = AiActionController.MAX_INPUT_CHARS

        /** The kb-api prompt `/v1/polish` runs, for `/v1/ai/report`. */
        const val PROMPT_POLISH = "polish_translate@1"

        private const val DAY_MS = 86_400_000L

        /** The cursor is at the span: inside it, at its end, or after one trailing space. */
        internal fun nextUtcDay(now: Long): Long = (Math.floorDiv(now, DAY_MS) + 1) * DAY_MS
    }
}

/**
 * Reads one string field out of the model's JSON as it streams in (`/v1/polish` relays the raw JSON text), for the
 * preview. Returns the value so far (an unfinished escape at the end is left out), or null before the field starts.
 */
internal object PolishJson {
    fun partialString(text: CharSequence, key: String): String? {
        val raw = text.toString()
        val needle = "\"$key\""
        var at = raw.indexOf(needle)
        while (at >= 0) {
            var i = at + needle.length
            while (i < raw.length && raw[i].isWhitespace()) i++
            if (i < raw.length && raw[i] == ':') {
                i++
                while (i < raw.length && raw[i].isWhitespace()) i++
                if (i >= raw.length) return null
                if (raw[i] != '"') return null
                return readString(raw, i + 1)
            }
            if (i >= raw.length) return null
            at = raw.indexOf(needle, at + 1)
        }
        return null
    }

    private fun readString(raw: String, from: Int): String {
        val out = StringBuilder()
        var i = from
        while (i < raw.length) {
            val c = raw[i]
            when {
                c == '"' -> return out.toString()
                c == '\\' -> {
                    if (i + 1 >= raw.length) return out.toString()
                    when (val e = raw[i + 1]) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        'r' -> out.append('\r')
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'u' -> {
                            if (i + 6 > raw.length) return out.toString()
                            val code = raw.subSequence(i + 2, i + 6).toString().toIntOrNull(16) ?: return out.toString()
                            out.append(code.toChar())
                            i += 6
                            continue
                        }
                        else -> out.append(e)
                    }
                    i += 2
                    continue
                }
                else -> out.append(c)
            }
            i++
        }
        return out.toString()
    }
}
