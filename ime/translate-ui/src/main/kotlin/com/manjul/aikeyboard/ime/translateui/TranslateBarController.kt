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
                pipeline.onInput(text)
                refresh()
            }
        }
        refresh()
    }

    fun close(reason: CloseReason) {
        when (reason) {
            CloseReason.SENSITIVE -> {
                closeBar(clearBuffer = true)
                cache?.clear()
            }
            CloseReason.USER, CloseReason.FIELD_CHANGE -> closeBar(clearBuffer = true)
            // The keyboard went away: keep the text for when it comes back.
            CloseReason.HIDDEN -> closeBar(clearBuffer = false)
        }
    }

    /** ✓ or Enter: commits the latest good preview once. */
    fun insert() {
        if (!open || !live()) return
        val g = good ?: return
        val source = buffer.text.value
        val state = pipeline.state.value
        // Only a preview of exactly what is in the buffer now, for the pair and field shown now.
        if (g.source != source || g.pair != currentPair || g.generation != openGeneration || state.output != g.text) {
            return
        }
        // Each insert stands alone: never joined to an earlier one by deleting a space.
        span.startSession()
        span.commit(g.text)
        ownWrite.recordCommit(written = g.text, restore = source)
        insertUpdatePending = true
        good = null
        buffer.clear()
        pipeline.reset()
        analytics(
            EventCatalog.translateDone(
                TranslateSurface.BAR,
                g.engine.toParam(),
                src = g.detected.tag,
                tgt = g.pair.target.tag,
                chars = source.length,
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
        val restore = ownWrite.undo()
        insertUpdatePending = false
        if (restore != null) buffer.replace(restore)
        refresh()
    }

    fun swapPair() {
        if (!open) return
        currentPair = currentPair.swapped()
        onPairChange(currentPair)
        pipeline.retry()
        refresh()
    }

    fun setMode(m: BarMode) {
        if (m == currentMode) return
        currentMode = m
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
        val result = router.translate(text, requestPair, seq)
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
            is TranslateResult.Failed -> if (result.kind != FailKind.POLICY) {
                analytics(EventCatalog.translateError(TranslateSurface.BAR, result.kind.toReason()))
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
        val f = finished
        if (state.status == BarStatus.Ok && !state.inFlight && f != null && f.seq == state.seq &&
            f.text == state.output && f.generation == openGeneration
        ) {
            if (good !== f) {
                good = f
                if (currentMode == BarMode.LIVE && f.pair == currentPair && live()) span.update(f.text)
            }
        } else if (state.output == null) {
            good = null
            // The buffer went blank: our live preview goes with it.
            if (currentMode == BarMode.LIVE) span.discard()
        }
        refresh()
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
