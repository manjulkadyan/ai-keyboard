/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LatencyStatsTest {
    private val oneToHundred = (1L..100L).toList()

    @Test fun nearestRankPercentilesOfOneToHundred() {
        assertEquals(90L, LatencyStats.percentile(oneToHundred, 0.90))
        assertEquals(50L, LatencyStats.percentile(oneToHundred, 0.50))
        assertEquals(100L, LatencyStats.percentile(oneToHundred, 1.0))
        assertEquals(1L, LatencyStats.percentile(oneToHundred, 0.0))
    }

    @Test fun orderOfSamplesDoesNotMatter() {
        assertEquals(90L, LatencyStats.percentile(oneToHundred.shuffled(kotlin.random.Random(7)), 0.90))
    }

    @Test fun nearestRankRoundsUp() {
        // 10 samples: rank of p90 is ceil(0.9 * 10) = 9, rank of p50 is 5, rank of p95 is ceil(9.5) = 10.
        val samples = listOf(10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L, 90L, 100L)
        assertEquals(90L, LatencyStats.percentile(samples, 0.90))
        assertEquals(50L, LatencyStats.percentile(samples, 0.50))
        assertEquals(100L, LatencyStats.percentile(samples, 0.95))
    }

    @Test fun singleSampleIsEveryPercentile() {
        assertEquals(42L, LatencyStats.percentile(listOf(42L), 0.5))
        assertEquals(42L, LatencyStats.percentile(listOf(42L), 0.9))
    }

    @Test fun emptySamplesAreAnError() {
        assertFailsWith<IllegalArgumentException> { LatencyStats.percentile(emptyList(), 0.9) }
    }

    @Test fun percentileOutsideZeroToOneIsAnError() {
        assertFailsWith<IllegalArgumentException> { LatencyStats.percentile(oneToHundred, 1.5) }
        assertFailsWith<IllegalArgumentException> { LatencyStats.percentile(oneToHundred, -0.1) }
    }
}
