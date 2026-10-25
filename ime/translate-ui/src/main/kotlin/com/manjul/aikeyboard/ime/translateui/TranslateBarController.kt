/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.translateui

import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.analytics.RateSampler
import com.manjul.aikeyboard.core.analytics.TranslateEngineParam
import com.manjul.aikeyboard.core.analytics.TranslateErrorReason
import com.manjul.aikeyboard.core.analytics.TranslateSurface
import com.manjul.aikeyboard.core.datastore.BarMode
import com.manjul.aikeyboard.core.translate.EngineId
import com.manjul.aikeyboard.core.translate.FailKind
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.translate.LangPair
import com.manjul.aikeyboard.core.translate.ModelSizes
import com.manjul.aikeyboard.core.translate.TranslateResult
import com.manjul.aikeyboard.core.translate.TranslationCache
import com.manjul.aikeyboard.core.translate.bar.BarStatus
import com.manjul.aikeyboard.core.translate.bar.DebouncePolicy
import com.manjul.aikeyboard.core.translate.bar.PipelineState
import com.manjul.aikeyboard.core.translate.bar.TranslatePipeline
import com.manjul.aikeyboard.ime.editing.ComposingSpanController
import com.manjul.aikeyboard.ime.editing.OwnWriteUndo
import com.manjul.aikeyboard.ime.editing.TextTarget
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the Translate bar: the buffer, the debounced translate calls, the preview, Insert/✓, undo of our own
 * insert, live composing and the failure chips. Main thread only. Buffer, preview and translations are user
 * content: never logged.
 *
 * Host safety (TRD §4.1.3, PRD FR-13 AC1): in [BarMode.PREVIEW] the host changes only on [insert]; in
 * [BarMode.LIVE] the preview goes into a composing region we started ([ComposingSpanController]) and is removed only
 * while still verified ours. The only other deletion is [undo] of our own insert ([OwnWriteUndo]).
 *
 * Privacy (PC-1): [open] is refused, and every request and every write is re-checked, against the live [decision]
 * and the field [generation] taken when the bar opened. A result is used only if both still hold and its sequence
 * number is the latest ([TranslatePipeline]); otherwise the bar resets and the result is dropped.
 *
 * ✓ or Enter before the preview of the buffer is ready sets a pending commit ([BarUiState.Open.commitPending]) and
 * sends the buffer at once: the translation is written when it arrives, exactly as [insert] would write it. Typing or
 * editing the buffer, a swap, a mode change, any close, a field change or generation bump, a policy restriction or a
 * failed request cancels it first, and nothing is written. An empty translation is never written.
 *
 * Analytics: `translate_done.offline` is true when the translation ran without network, i.e. on the on-device
 * engine ([EngineId.MLKIT]); it does not say whether the device had connectivity.
 *
 * @param pair the shared language pair, read on [open]; [swapPair] reports the new pair through [onPairChange].
 * @param mode the bar mode, read on [open]; [setMode] reports the new mode through [onModeChange].
 * @param downloaded the languages already on the device, for the download size of the "Download" chip.
 * @param cache the router's translation cache, cleared when the field turns sensitive.
 * @param onFirstTranslate called once per controller after the first insert; returns true if it was the first
 *   translation ever (the app persists that), and the controller then logs `first_translate`.
 */
class TranslateBarController(
    private val scope: CoroutineScope,
    private val router: TranslateCall,
    private val decision: () -> PolicyDecision,
    private val generation: () -> Int,
    private val target: TextTarget,
    private val pair: () -> LangPair,
    private val mode: () -> BarMode,
    private val debounce: DebouncePolicy,
    private val analytics: (AnalyticsEvent) -> Unit,
    private val previewSampler: RateSampler,
    private val clock: () -> Long,
    private val onDownload: (LangPair) -> Unit,
    private val onOpenOfflineLanguages: () -> Unit,
    private val onPairChange: (LangPair) -> Unit = {},
    private val onModeChange: (BarMode) -> Unit = {},
    private val downloaded: () -> Set<Lang> = { emptySet() },
    private val cache: TranslationCache? = null,
    private val onFirstTranslate: () -> Boolean = { false },
) {
    /** A finished translation: what was translated, for which pair and field. Text is user content. */
    private class Finished(
        val seq: Long,
        val source: String,
        val pair: LangPair,
        val generation: Int,
        val text: String,
        val detected: Lang,
        val engine: EngineId,
    ) {
        override fun toString(): String = "Finished(seq=$seq, pair=$pair, chars=${text.length})"
    }

    private val _ui = MutableStateFlow<BarUiState>(BarUiState.Hidden)
    val ui: StateFlow<BarUiState> = _ui.asStateFlow()

    val buffer: BarBuffer = BarBuffer()

    private val span = ComposingSpanController(target)
    private val ownWrite = OwnWriteUndo(target, clock, UNDO_WINDOW_MS)
    private val pipeline = TranslatePipeline(scope, debounce, needsDownload = ::downloadOffer, translate = ::request)

    private var open = false
    private var openGeneration = 0
    private var currentPair: LangPair = pair()
    private var currentMode: BarMode = mode()

    /** The latest successful result from the translate call, applied or not. */
    private var finished: Finished? = null

    /** The latest result the pipeline applied while it still matched this field: what [insert] may commit. */
    private var good: Finished? = null

    /** The next selection update without a composing region is our insert's; any other one means a host edit. */
    private var insertUpdatePending = false
    private var firstTranslateChecked = false

    /** Counts inserts, for [BarUiState.Open.undoId]. */
    private var inserts = 0L

    /** Counts sensitive closes, so a request that spans one can drop what the router cached meanwhile. */
    private var sensitiveCloses = 0

    /** ✓ or Enter came before the translation of this buffer text, pair and field: commit it when it arrives. */
    private class Pending(val source: String, val pair: LangPair, val generation: Int) {
        override fun toString(): String = "Pending(chars=${source.length}, pair=$pair)"
    }

    private var pending: Pending? = null

    /** The text of the request now at the engine, or null. User content: never logged. */
    private var requestText: String? = null

    private var bufferJob: Job? = null
    private var undoJob: Job? = null

    init {
        scope.launch { pipeline.state.collect(::onPipeline) }
    }

    /** True while the bar is open and keys go to [buffer] instead of the host. */
    val capturingInput: Boolean get() = open

    /** No-op when the live decision refuses translation. */
    fun open() {
        if (open || !decision().allowTranslate) return
        open = true
        openGeneration = generation()
        currentPair = pair()
        currentMode = mode()
        bufferJob = scope.launch {
            buffer.text.collect { text ->
                // Typing or editing after Enter: the user is not done, so nothing is written.
                if (pending?.source != text) pending = null
                pipeline.onInput(text)
                refresh()
            }
        }
        refresh()
    }

    /** Defaults to [CloseReason.USER], so the bar's close button can take `controller::close`. */
    fun close(reason: CloseReason = CloseReason.USER) {
        when (reason) {
            CloseReason.SENSITIVE -> {
                sensitiveCloses++
                closeBar(clearBuffer = true)
                cache?.clear()
            }
            CloseReason.USER, CloseReason.FIELD_CHANGE -> closeBar(clearBuffer = true)
            // The keyboard went away: keep the text for when it comes back.
            CloseReason.HIDDEN -> closeBar(clearBuffer = false)
        }
    }

    /**
     * ✓ or Enter: commits the latest good preview once. Without a preview of exactly the buffer (Enter before the
     * debounce, or after a failed request) nothing is written yet: the commit becomes pending and the buffer is sent
     * for translation now instead of after the debounce; its result is committed when it arrives (see the class
     * doc for what cancels it). Another ✓ while pending changes nothing.
     */
    fun insert() {
        if (!open || !live()) return
        val source = buffer.text.value
        val state = pipeline.state.value
        val g = applied(state) ?: good
        // Only a preview of exactly what is in the buffer now, for the pair and field shown now.
        if (g != null && g.source == source && g.pair == currentPair && g.generation == openGeneration &&
            state.output == g.text
        ) {
            pending = null
            // An empty translation is never written.
            if (g.text.isNotEmpty()) commit(g, source)
            return
        }
        if (source.isBlank()) return
        if (pending?.let { it.source == source && it.pair == currentPair && it.generation == openGeneration } == true) {
            return
        }
        pending = Pending(source, currentPair, openGeneration)
        if (!(state.inFlight && requestText == source)) pipeline.retry()
        refresh()
    }

    /** Writes [g], the translation of [source], records the undo and logs `translate_done`. */
    private fun commit(g: Finished, source: String) {
        pending = null
        // Each insert stands alone: never joined to an earlier one by deleting a space.
        span.startSession()
        span.commit(g.text)
        ownWrite.recordCommit(written = g.text, restore = source)
        inserts++
        insertUpdatePending = true
        good = null
        finished = null
        buffer.clear()
        pipeline.reset()
        analytics(
            EventCatalog.translateDone(
                TranslateSurface.BAR,
                g.engine.toParam(),
                src = g.detected.tag,
                tgt = g.pair.target.tag,
                chars = source.length,
                // Translated without network: the on-device engine.
                offline = g.engine == EngineId.MLKIT,
            ),
        )
        if (!firstTranslateChecked) {
            firstTranslateChecked = true
            if (onFirstTranslate()) analytics(EventCatalog.firstTranslate(TranslateSurface.BAR))
        }
        undoJob?.cancel()
        undoJob = scope.launch {
            delay(UNDO_WINDOW_MS + 1)
            refresh()
        }
        refresh()
    }

    /** Removes our last insert if it is still right before the cursor, and puts its source back in the buffer. */
    fun undo() {
        if (!open || !live()) return
        // A live preview typed since the insert sits right before the cursor: it goes first (verified ours), so the
        // check below compares against our insert and not against the preview.
        if (ownWrite.available) span.discard()
        val restore = ownWrite.undo()
        insertUpdatePending = false
        if (restore != null) {
            pending = null
            buffer.replace(restore)
        } else if (currentMode == BarMode.LIVE) {
            // Nothing was undone: the live preview comes back. (After a removal it waits for the next preview, so
            // the removal's selection update is not taken for a host edit of the new region.)
            matchingGood()?.let { span.update(it.text) }
        }
        refresh()
    }

    fun swapPair() {
        if (!open) return
        currentPair = currentPair.swapped()
        pending = null
        onPairChange(currentPair)
        pipeline.retry()
        refresh()
    }

    fun setMode(m: BarMode) {
        if (m == currentMode) return
        currentMode = m
        pending = null
        onModeChange(m)
        if (open && live()) {
            if (m == BarMode.PREVIEW) {
                span.discard()
            } else {
                matchingGood()?.let { span.update(it.text) }
            }
        }
        refresh()
    }

    fun retry() {
        if (!open || !live()) return
        pipeline.retry()
    }

    /** The user tapped the download chip; downloads never start on their own. */
    fun download() {
        if (!open) return
        val status = pipeline.state.value.status
        onDownload(if (status is BarStatus.NeedsDownload) status.pair else currentPair)
    }

    /** The user tapped the offline chip. */
    fun openOfflineLanguages() {
        if (!open) return
        onOpenOfflineLanguages()
    }

    /** Forwarded from `onUpdateSelection`, for live composing and undo invalidation. */
    fun onSelectionUpdate(newSelStart: Int, newSelEnd: Int, candStart: Int, candEnd: Int) {
        span.onSelectionUpdate(newSelStart, newSelEnd, candStart, candEnd)
        // Updates with a composing region are our earlier previews' (a host edit would end our region anyway).
        if (candStart >= 0) return
        if (insertUpdatePending) {
            insertUpdatePending = false
        } else if (ownWrite.available) {
            ownWrite.invalidate()
            refresh()
        }
    }

    fun onPolicyRestricted(d: PolicyDecision) {
        if (!d.allowTranslate) close(CloseReason.SENSITIVE)
    }

    /** A new field: the buffer is cleared, pair and mode are kept. */
    fun onFieldChanged() {
        span.discard()
        ownWrite.invalidate()
        insertUpdatePending = false
        pending = null
        good = null
        finished = null
        openGeneration = generation()
        buffer.clear()
        pipeline.reset()
        refresh()
    }

    /**
     * The live checks before every request and write: the field may still be translated and is still the one the bar
     * opened in. A sensitive field closes the bar; a new field resets it. Returns whether to go on.
     */
    private fun live(): Boolean {
        if (!decision().allowTranslate) {
            close(CloseReason.SENSITIVE)
            return false
        }
        if (generation() != openGeneration) {
            onFieldChanged()
            return false
        }
        return true
    }

    private fun closeBar(clearBuffer: Boolean) {
        // Our live preview goes only while verified ours; preview mode never wrote anything.
        span.discard()
        ownWrite.invalidate()
        insertUpdatePending = false
        bufferJob?.cancel()
        bufferJob = null
        undoJob?.cancel()
        undoJob = null
        pending = null
        good = null
        finished = null
        if (clearBuffer) buffer.clear()
        pipeline.reset()
        open = false
        _ui.value = BarUiState.Hidden
    }

    private suspend fun request(text: String, seq: Long): TranslateResult {
        // A refusal resets the pipeline, so this result is dropped as stale.
        if (!open || !live()) return TranslateResult.Failed(FailKind.POLICY)
        val requestPair = currentPair
        val requestGeneration = openGeneration
        val requestSensitiveCloses = sensitiveCloses
        requestText = text
        val result = try {
            router.translate(text, requestPair, seq)
        } finally {
            requestText = null
            // The router caches a result as the engine returns it, which can be after the field turned sensitive
            // and the cache was cleared (an ML Kit Task cannot be cancelled): clear it again.
            if (sensitiveCloses != requestSensitiveCloses) cache?.clear()
        }
        if (!open || !live() || requestGeneration != openGeneration) {
            return TranslateResult.Failed(FailKind.POLICY)
        }
        when (result) {
            is TranslateResult.Ok -> {
                finished =
                    Finished(seq, text, requestPair, requestGeneration, result.text, result.source, result.engine)
                if (previewSampler.sample()) {
                    analytics(
                        EventCatalog.translatePreview(
                            TranslateSurface.BAR,
                            result.engine.toParam(),
                            result.source.tag,
                            requestPair.target.tag,
                        ),
                    )
                }
            }
            is TranslateResult.Failed -> {
                // The translation Enter waits for failed: nothing is written, the status chip says why.
                if (pending?.source == text) pending = null
                if (result.kind != FailKind.POLICY) {
                    analytics(EventCatalog.translateError(TranslateSurface.BAR, result.kind.toReason()))
                }
            }
        }
        return result
    }

    private fun downloadOffer(): BarStatus.NeedsDownload {
        val p = currentPair
        return BarStatus.NeedsDownload(p, ModelSizes.bytes(ModelSizes.missingFor(p, downloaded())))
    }

    private fun onPipeline(state: PipelineState) {
        if (!open) return
        val f = applied(state)
        if (f != null) {
            if (good !== f) {
                good = f
                if (currentMode == BarMode.LIVE && f.pair == currentPair && live()) span.update(f.text)
                commitPending(f)
            }
        } else if (state.output == null) {
            good = null
            // The buffer went blank: our live preview goes with it.
            if (currentMode == BarMode.LIVE) span.discard()
        }
        refresh()
    }

    /** The latest result if the pipeline applied it (latest seq, no failure since) for this field, else null. */
    private fun applied(state: PipelineState): Finished? = finished?.takeIf {
        state.status == BarStatus.Ok && !state.inFlight && it.seq == state.seq && it.text == state.output &&
            it.generation == openGeneration
    }

    /** The translation a pending ✓ waits for arrived: commit it if it is still for this buffer, pair and field. */
    private fun commitPending(f: Finished) {
        val p = pending ?: return
        if (!open || !live()) return
        val source = buffer.text.value
        if (f.source != p.source || f.source != source || f.pair != p.pair || f.pair != currentPair ||
            f.generation != p.generation || f.generation != openGeneration
        ) {
            return
        }
        pending = null
        // An empty translation is never written.
        if (f.text.isNotEmpty()) commit(f, source)
    }

    private fun matchingGood(): Finished? =
        good?.takeIf { it.pair == currentPair && it.generation == openGeneration && it.source == buffer.text.value }

    private fun refresh() {
        if (!open) {
            _ui.value = BarUiState.Hidden
            return
        }
        val state = pipeline.state.value
        _ui.value = BarUiState.Open(
            input = buffer.text.value,
            preview = state.output,
            pair = currentPair,
            mode = currentMode,
            status = state.status,
            undoAvailable = ownWrite.available,
            inFlight = state.inFlight,
            undoId = inserts,
            commitPending = pending != null,
        )
    }

    private companion object {
        /** TRD §4.1.3: undo of our insert is offered for 5 s. */
        const val UNDO_WINDOW_MS = 5_000L

        fun EngineId.toParam(): TranslateEngineParam = when (this) {
            EngineId.MLKIT -> TranslateEngineParam.ON_DEVICE
            EngineId.CLOUD -> TranslateEngineParam.CLOUD
        }

        fun FailKind.toReason(): TranslateErrorReason =
            TranslateErrorReason.entries.firstOrNull { it.value == analytics } ?: TranslateErrorReason.OTHER
    }
}
