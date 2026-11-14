/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.theme

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * A colour in HSV (S5 Task 8, the colour picker). [h] is the hue in degrees (any value; it wraps, so 360 is 0),
 * [s], [v] and [a] are saturation, value and alpha in `0..1` (clamped on conversion).
 */
data class Hsv(val h: Float, val s: Float, val v: Float, val a: Float = 1f) {
    /** The ARGB value, `0..0xFFFFFFFF`. */
    fun toArgb(): Long {
        val hue = ((h % 360f) + 360f) % 360f
        val sat = s.coerceIn(0f, 1f)
        val value = v.coerceIn(0f, 1f)
        val c = value * sat
        val x = c * (1 - abs((hue / 60f) % 2f - 1))
        val m = value - c
        val (r, g, b) = when ((hue / 60f).toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return (channel(a.coerceIn(0f, 1f)) shl 24) or (channel(r + m) shl 16) or (channel(g + m) shl 8) or
            channel(b + m)
    }

    companion object {
        /** The HSV of [argb]; a grey has hue 0 and saturation 0. */
        fun fromArgb(argb: Long): Hsv {
            val a = ((argb ushr 24) and 0xFF) / 255f
            val r = ((argb shr 16) and 0xFF) / 255f
            val g = ((argb shr 8) and 0xFF) / 255f
            val b = (argb and 0xFF) / 255f
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val delta = max - min
            val h = when {
                delta == 0f -> 0f
                max == r -> 60f * (((g - b) / delta) % 6f)
                max == g -> 60f * ((b - r) / delta + 2f)
                else -> 60f * ((r - g) / delta + 4f)
            }
            val s = if (max == 0f) 0f else delta / max
            return Hsv(h = (h + 360f) % 360f, s = s, v = max, a = a)
        }

        private fun channel(f: Float): Long = (f * 255f).roundToLong().coerceIn(0L, 255L)
    }
}
