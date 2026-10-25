/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.devtools

import com.manjul.aikeyboard.core.translate.EngineId
import com.manjul.aikeyboard.core.translate.FailKind
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.translate.LangPair
import com.manjul.aikeyboard.core.translate.LatencyCheck
import com.manjul.aikeyboard.core.translate.Support
import com.manjul.aikeyboard.core.translate.TranslateRequest
import com.manjul.aikeyboard.core.translate.TranslateResult
import com.manjul.aikeyboard.core.translate.TranslationEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TranslateLatencyProbeTest {
    private val enUr = LangPair(Lang("en"), Lang("ur"))
    private val sentences = (1..50).map { "Secret sentence number $it." }

    /** Each translate call advances the fake clock by its 1-based call index in ms. */
    private class TimedEngine(private val clock: LongArray, private val fail: FailKind? = null) : TranslationEngine {
        val requests = mutableListOf<TranslateRequest>()
        override val id = EngineId.MLKIT
        override suspend fun supports(pair: LangPair) = Support.ON_DEVICE
        override suspend fun translate(req: TranslateRequest): TranslateResult {
            requests += req
            clock[0] += requests.size * 1_000_000L
            return fail?.let { TranslateResult.Failed(it) } ?: TranslateResult.Ok("translated", req.source!!, id)
        }
    }

    @Test fun warmsUpThenTimesEverySentenceWithoutCacheHits() = runTest {
        val clock = longArrayOf(0)
        val engine = TimedEngine(clock)
        val probe = TranslateLatencyProbe(engine, corpus = { sentences }, nanoTime = { clock[0] })

        val lines = probe.run(listOf(enUr))

        assertEquals(LatencyCheck.WARM_UP_CALLS + LatencyCheck.TIMED_CALLS, engine.requests.size)
        // Timed calls are 6..55 ms: p50 is the 25th (30 ms), p90 the 45th (50 ms).
        assertEquals(listOf("en-ur p50=30 p90=50 n=50"), lines)
    }

    @Test fun failureReportsTheReasonAndNoText() = runTest {
        val clock = longArrayOf(0)
        val probe = TranslateLatencyProbe(TimedEngine(clock, FailKind.NO_PACK), corpus = { sentences }, nanoTime = { clock[0] })

        val lines = probe.run(listOf(enUr))

        assertEquals(listOf("en-ur no_pack"), lines)
        assertFalse(lines.any { "Secret" in it || "translated" in it })
    }

    @Test fun missingCorpusIsReported() = runTest {
        val clock = longArrayOf(0)
        val engine = TimedEngine(clock)
        val probe = TranslateLatencyProbe(engine, corpus = { null }, nanoTime = { clock[0] })

        assertEquals(listOf("en-ur no_corpus"), probe.run(listOf(enUr)))
        assertEquals(0, engine.requests.size)
    }

    @Test fun reportsEachPairInOrder() = runTest {
        val clock = longArrayOf(0)
        val seen = mutableListOf<LangPair>()
        val probe = TranslateLatencyProbe(TimedEngine(clock), corpus = { sentences }, nanoTime = { clock[0] })

        val lines = probe.run(LatencyCheck.Pairs) { seen += it }

        assertEquals(LatencyCheck.Pairs, seen)
        assertEquals(LatencyCheck.Pairs.map { "${it.source}-${it.target}" }, lines.map { it.substringBefore(' ') })
    }
}
