/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.theme

import kotlin.math.roundToLong

/**
 * The colour under an eyedropper (S5 Task 8). S5 ships no eyedropper UI (decision 16), so only this pure sampler and
 * its tests exist; a later sprint feeds it the pixels of a screenshot.
 */
object ColorSampler {
    /**
     * The average ARGB colour of the `(2 * radius + 1)` square around ([x], [y]) in [pixels], a row-major image
     * [width] pixels wide. The square, and a point outside the image, clamp to the image edges.
     */
    fun sample(pixels: IntArray, width: Int, x: Int, y: Int, radius: Int = 2): Long {
        require(width > 0 && pixels.isNotEmpty() && pixels.size % width == 0) { "not a width x height image" }
        require(radius >= 0) { "radius must not be negative" }
        val height = pixels.size / width
        val cx = x.coerceIn(0, width - 1)
        val cy = y.coerceIn(0, height - 1)
        val sums = LongArray(4)
        var count = 0
        for (py in (cy - radius).coerceAtLeast(0)..(cy + radius).coerceAtMost(height - 1)) {
            for (px in (cx - radius).coerceAtLeast(0)..(cx + radius).coerceAtMost(width - 1)) {
                val p = pixels[py * width + px].toLong() and 0xFFFFFFFFL
                for (i in 0..3) sums[i] += (p ushr (24 - 8 * i)) and 0xFF
                count++
            }
        }
        var out = 0L
        for (i in 0..3) {
            val avg = (sums[i].toDouble() / count).roundToLong().coerceIn(0L, 255L)
            out = out or (avg shl (24 - 8 * i))
        }
        return out
    }
}
