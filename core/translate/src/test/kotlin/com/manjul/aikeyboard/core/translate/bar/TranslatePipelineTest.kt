/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate.bar

import com.manjul.aikeyboard.core.translate.EngineId
import com.manjul.aikeyboard.core.translate.FailKind
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.translate.LangPair
import com.manjul.aikeyboard.core.translate.ModelSizes
import com.manjul.aikeyboard.core.translate.Origin
import com.manjul.aikeyboard.core.translate.TranslateRequest
import com.manjul.aikeyboard.core.translate.TranslateResult
import com.manjul.aikeyboard.core.translate.testing.FakeTranslationEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TranslatePipelineTest {
    private val en = Lang("en")
    private val es = Lang("es")

    private class Rig(
        val scope: TestScope,
        val engine: FakeTranslationEngine,
        debounce: DebouncePolicy,
        needsDownload: suspend () -> BarStatus.NeedsDownload? = { null },
    ) {
        /** (text, virtual time) of every call the pipeline made. */
        val callTimes = mutableListOf<Pair<String, Long>>()
        val pipeline = TranslatePipeline(scope.backgroundScope, debounce, needsDownload) { text, _ ->
            callTimes += text to scope.currentTime
            engine.translate(TranslateRequest(text, Lang("en"), Lang("es"), Origin.BAR))
        }
        val state get() = pipeline.state.value
    }

    private fun TestScope.rig(
        engine: FakeTranslationEngine = FakeTranslationEngine(),
        debounce: DebouncePolicy = DebouncePolicy.Default,
        needsDownload: suspend () -> BarStatus.NeedsDownload? = { null },
    ) = Rig(this, engine, debounce, needsDownload).also { runCurrent() }

    @Test fun keystrokesAreDebouncedIntoOneCallForTheLastText() = runTest {
        val r = rig()
        r.pipeline.onInput("h")
        advanceTimeBy(100)
        r.pipeline.onInput("he")
        advanceTimeBy(100)
        r.pipeline.onInput("hel")
        val lastKey = currentTime
        advanceTimeBy(449)
        runCurrent()
        assertTrue(r.callTimes.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("hel" to lastKey + 450), r.callTimes)
        assertEquals("<es>hel", r.state.output)
        assertEquals(BarStatus.Ok, r.state.status)
        assertFalse(r.state.inFlight)
    }

    @Test fun lowTierWaits600Ms() = runTest {
        val r = rig(debounce = DebouncePolicy.LowTier)
        r.pipeline.onInput("hel")
        advanceTimeBy(599)
        runCurrent()
        assertTrue(r.callTimes.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, r.callTimes.size)
    }

    @Test fun newInputCancelsTheSlowRequestAndOnlyOneIsInFlight() = runTest {
        val engine = FakeTranslationEngine(delays = { if (it == "hel") 2_000L else 10L })
        val r = rig(engine)
        r.pipeline.onInput("hel")
        advanceTimeBy(451)
        assertTrue(r.state.inFlight)
        assertEquals(1, engine.inFlight)
        r.pipeline.onInput("hello")
        advanceTimeBy(500)
        runCurrent()
        assertEquals(listOf("hel", "hello"), engine.calls.map { it.text })
        assertEquals(1, engine.maxInFlight)
        assertEquals("<es>hello", r.state.output)
        advanceTimeBy(5_000)
        assertEquals("<es>hello", r.state.output)
    }

    @Test fun aResultFromAnEngineThatIgnoresCancellationIsNeverShown() = runTest {
        val engine = FakeTranslationEngine(
            delays = { if (it == "old") 2_000L else 10L },
            ignoreCancellation = true,
        )
        val r = rig(engine)
        val outputs = mutableListOf<String?>()
        backgroundScope.launch { r.pipeline.state.collect { outputs += it.output } }
        r.pipeline.onInput("old")
        advanceTimeBy(500)
        r.pipeline.onInput("new")
        advanceTimeBy(10_000)
        runCurrent()
        // The engine finished "old" (it could not be cancelled), but its result was dropped.
        assertEquals(listOf("old", "new"), engine.calls.map { it.text })
        assertFalse("<es>old" in outputs)
        assertEquals("<es>new", r.state.output)
    }

    @Test fun lateResultAfterClearIsDroppedBySequenceNumber() = runTest {
        val engine = FakeTranslationEngine(delays = { 2_000L }, ignoreCancellation = true)
        val r = rig(engine)
        r.pipeline.onInput("hello")
        advanceTimeBy(500)
        assertTrue(r.state.inFlight)
        r.pipeline.onInput("")
        advanceTimeBy(5_000)
        runCurrent()
        assertNull(r.state.output)
        assertEquals(BarStatus.Idle, r.state.status)
        assertFalse(r.state.inFlight)
    }

    @Test fun failureKeepsTheLastGoodOutput() = runTest {
        val engine = FakeTranslationEngine(
            results = { req ->
                if (req.text == "hello") TranslateResult.Ok("hola", en, EngineId.MLKIT) else TranslateResult.Failed(FailKind.OFFLINE)
            },
        )
        val r = rig(engine)
        r.pipeline.onInput("hello")
        advanceTimeBy(500)
        assertEquals("hola", r.state.output)
        r.pipeline.onInput("hello there")
        advanceTimeBy(500)
        assertEquals("hola", r.state.output)
        assertEquals(BarStatus.Offline, r.state.status)
        assertFalse(r.state.inFlight)
    }

    @Test fun failureKindsMapToStatusChips() = runTest {
        val kinds = mapOf(
            "a" to FailKind.CLOUD_UNAVAILABLE,
            "b" to FailKind.TIMEOUT,
            "c" to FailKind.ENGINE,
            "d" to FailKind.NO_PACK,
        )
        val r = rig(FakeTranslationEngine(results = { TranslateResult.Failed(kinds.getValue(it.text)) }))
        val expected = mapOf(
            "a" to BarStatus.CloudOnlyPro,
            "b" to BarStatus.Error(FailKind.TIMEOUT),
            "c" to BarStatus.Error(FailKind.ENGINE),
            // No download info available: falls back to an error chip.
            "d" to BarStatus.Error(FailKind.NO_PACK),
        )
        for ((text, status) in expected) {
            r.pipeline.onInput(text)
            advanceTimeBy(500)
            assertEquals(status, r.state.status, text)
        }
    }

    @Test fun noPackOffersTheDownloadWithItsSize() = runTest {
        val pair = LangPair(Lang("ur"), Lang("hi"))
        val offer = BarStatus.NeedsDownload(pair, ModelSizes.bytes(ModelSizes.missingFor(pair, emptySet())))
        val r = rig(FakeTranslationEngine(results = { TranslateResult.Failed(FailKind.NO_PACK) }), needsDownload = { offer })
        r.pipeline.onInput("hello")
        advanceTimeBy(500)
        assertEquals(offer, r.state.status)
        assertEquals(90L * 1024 * 1024, (r.state.status as BarStatus.NeedsDownload).bytes)
        assertNull(r.state.output)
    }

    @Test fun blankInputClearsOutputWithoutACall() = runTest {
        val r = rig()
        r.pipeline.onInput("hi")
        advanceTimeBy(500)
        assertEquals(1, r.callTimes.size)
        r.pipeline.onInput("   ")
        assertNull(r.state.output)
        assertEquals(BarStatus.Idle, r.state.status)
        advanceTimeBy(1_000)
        assertEquals(1, r.callTimes.size)
    }

    @Test fun sameTextTwiceIsTranslatedOnce() = runTest {
        val r = rig()
        r.pipeline.onInput("hi")
        advanceTimeBy(500)
        r.pipeline.onInput("hi")
        advanceTimeBy(500)
        r.pipeline.onInput("hi!")
        r.pipeline.onInput("hi")
        advanceTimeBy(500)
        assertEquals(listOf("hi"), r.callTimes.map { it.first })
    }

    @Test fun sameTextAfterClearIsTranslatedAgain() = runTest {
        val r = rig()
        r.pipeline.onInput("hi")
        advanceTimeBy(500)
        r.pipeline.onInput("")
        r.pipeline.onInput("hi")
        advanceTimeBy(500)
        assertEquals(listOf("hi", "hi"), r.callTimes.map { it.first })
        assertEquals("<es>hi", r.state.output)
    }

    @Test fun retryResendsTheCurrentInputOnceWithoutDebounce() = runTest {
        val r = rig()
        r.pipeline.onInput("hi")
        advanceTimeBy(500)
        r.pipeline.retry()
        runCurrent()
        assertEquals(2, r.callTimes.size)
        advanceTimeBy(5_000)
        assertEquals(2, r.callTimes.size)
    }

    @Test fun resetClearsStateAndCancelsTheRequest() = runTest {
        val engine = FakeTranslationEngine(delays = { 2_000L })
        val r = rig(engine)
        r.pipeline.onInput("hi")
        advanceTimeBy(500)
        assertEquals(1, engine.inFlight)
        r.pipeline.reset()
        runCurrent()
        assertEquals(0, engine.inFlight)
        assertEquals("", r.state.input)
        assertNull(r.state.output)
        assertFalse(r.state.inFlight)
        assertEquals(BarStatus.Idle, r.state.status)
        advanceTimeBy(5_000)
        assertNull(r.state.output)
        assertEquals(1, engine.calls.size)
    }

    @Test fun engineExceptionBecomesAnErrorAndKeepsOutput() = runTest {
        var fail = false
        val pipeline = TranslatePipeline(backgroundScope, DebouncePolicy.Default) { text, _ ->
            if (fail) throw IllegalStateException("secret $text")
            TranslateResult.Ok("ok", en, EngineId.MLKIT)
        }
        runCurrent()
        pipeline.onInput("a")
        advanceTimeBy(500)
        fail = true
        pipeline.onInput("b")
        advanceTimeBy(500)
        assertEquals("ok", pipeline.state.value.output)
        assertEquals(BarStatus.Error(FailKind.ENGINE), pipeline.state.value.status)
    }

    @Test fun stateToStringHasNoText() {
        val s = PipelineState("secret input", "secret output", 3, false, BarStatus.Ok).toString()
        assertFalse("secret" in s)
    }

    @Test fun debouncePolicyForDevice() {
        assertEquals(DebouncePolicy.LowTier, DebouncePolicy.forDevice(lowRam = true, totalRamBytes = 8L shl 30))
        assertEquals(DebouncePolicy.LowTier, DebouncePolicy.forDevice(lowRam = false, totalRamBytes = 3L shl 30))
        assertEquals(DebouncePolicy.Default, DebouncePolicy.forDevice(lowRam = false, totalRamBytes = 4L shl 30))
        assertEquals(DebouncePolicy.Default, DebouncePolicy.forDevice(lowRam = false, totalRamBytes = 0))
        assertEquals(450, DebouncePolicy.Default.debounceMs)
        assertEquals(600, DebouncePolicy.Translator.debounceMs)
    }
}
