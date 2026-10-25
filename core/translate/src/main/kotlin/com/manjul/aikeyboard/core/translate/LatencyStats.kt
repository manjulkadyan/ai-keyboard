/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate

import kotlin.math.ceil

/**
 * Percentiles for the day-1 translate latency check (TRD §8, R-03) and the devtools probe. Timings only, no
 * text.
 */
object LatencyStats {
    /** Absorbs floating-point error in `p * n` so `0.9 * 10` ranks 9, not 10. */
    private const val EPSILON = 1e-9

    /**
     * The nearest-rank percentile of [samplesMs]: the smallest sample such that at least [p] of all samples are
     * less than or equal to it (`p90` of `1..100` is `90`). [p] is a fraction in `0.0..1.0`; `0.0` gives the
     * smallest sample.
     */
    fun percentile(samplesMs: List<Long>, p: Double): Long {
        require(samplesMs.isNotEmpty()) { "no samples" }
        require(p in 0.0..1.0) { "p must be in 0.0..1.0" }
        val sorted = samplesMs.sorted()
        val rank = ceil(p * sorted.size - EPSILON).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }
}
