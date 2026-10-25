/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate.bar

import com.manjul.aikeyboard.core.translate.FailKind
import com.manjul.aikeyboard.core.translate.TranslateResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * The debounced translate loop behind the keyboard bar and the Translator screen (TRD §4.1.3, PRD FR-13 AC2).
 * Input is user content, never logged.
 *
 * - Input is debounced by [debounce] and identical consecutive input is translated once.
 * - `mapLatest` cancels the previous request when new input arrives, so at most one request is in flight.
 * - Every request gets a sequence number; a result is applied only while its number is the latest, so a
 *   result from an engine that ignored cancellation (ML Kit `Task`s cannot be cancelled) is never shown.
 * - A failure keeps the last good [PipelineState.output] and only changes the status; nothing is erased.
 * - Blank input clears the output at once and makes no call. [retry] re-sends the current input without
 *   waiting for the debounce; [reset] clears everything and cancels the in-flight request.
 *
 * @param translate runs one request; receives the text and the request's sequence number.
 * @param needsDownload builds the download offer after a [FailKind.NO_PACK] failure, or `null` when unknown
 *   (the status is then [BarStatus.Error]).
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class TranslatePipeline(
    scope: CoroutineScope,
    private val debounce: DebouncePolicy,
    private val needsDownload: suspend () -> BarStatus.NeedsDownload? = { null },
    private val translate: suspend (String, Long) -> TranslateResult,
) {
    /** [nonce] changes on retry/reset/blank so the same text can be requested again after them. */
    private data class Request(val text: String, val nonce: Long, val immediate: Boolean) {
        override fun toString(): String = "Request(chars=${text.length}, nonce=$nonce, immediate=$immediate)"
    }

    private val _state = MutableStateFlow(PipelineState.Initial)
    val state: StateFlow<PipelineState> = _state.asStateFlow()

    private val requests = MutableStateFlow(Request(text = "", nonce = 0, immediate = true))
    private val seqCounter = AtomicLong(0)
    private val nonceCounter = AtomicLong(0)

    init {
        scope.launch {
            requests
                .debounce { if (it.immediate) 0L else debounce.debounceMs }
                .distinctUntilChanged { old, new -> old.text == new.text && old.nonce == new.nonce }
                .mapLatest(::run)
                .collect()
        }
    }

    fun onInput(text: String) {
        if (text.isBlank()) {
            clear(text)
            return
        }
        _state.update { it.copy(input = text) }
        requests.value = Request(text, nonceCounter.get(), immediate = false)
    }

    /** Sends the current input again, now. No-op for blank input. */
    fun retry() {
        val text = _state.value.input
        if (text.isBlank()) return
        requests.value = Request(text, nonceCounter.incrementAndGet(), immediate = true)
    }

    fun reset() {
        clear("")
    }

    private fun clear(input: String) {
        // Bumping seq drops any in-flight result at once; the blank request cancels it in mapLatest.
        val seq = seqCounter.incrementAndGet()
        _state.value = PipelineState.Initial.copy(input = input, seq = seq)
        requests.value = Request("", nonceCounter.incrementAndGet(), immediate = true)
    }

    private suspend fun run(request: Request) {
        if (request.text.isBlank()) return
        val seq = seqCounter.incrementAndGet()
        _state.update { it.copy(seq = seq, inFlight = true) }
        val result = try {
            translate(request.text, seq)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Engines return Failed instead of throwing; this is a guard. The message is dropped (PC-10).
            TranslateResult.Failed(FailKind.ENGINE)
        }
        currentCoroutineContext().ensureActive()
        val status = when (result) {
            is TranslateResult.Ok -> BarStatus.Ok
            is TranslateResult.Failed -> statusFor(result.kind)
        }
        _state.update { current ->
            if (current.seq != seq) return@update current
            when (result) {
                is TranslateResult.Ok -> current.copy(output = result.text, inFlight = false, status = status)
                is TranslateResult.Failed -> current.copy(inFlight = false, status = status)
            }
        }
    }

    private suspend fun statusFor(kind: FailKind): BarStatus = when (kind) {
        FailKind.NO_PACK -> needsDownload() ?: BarStatus.Error(kind)
        FailKind.OFFLINE -> BarStatus.Offline
        FailKind.CLOUD_UNAVAILABLE -> BarStatus.CloudOnlyPro
        else -> BarStatus.Error(kind)
    }
}
