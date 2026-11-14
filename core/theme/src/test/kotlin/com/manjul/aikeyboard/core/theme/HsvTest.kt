/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.theme

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HsvTest {
    private fun assertWithinOneStep(expected: Long, actual: Long) {
        for (shift in listOf(24, 16, 8, 0)) {
            val e = (expected ushr shift) and 0xFF
            val a = (actual ushr shift) and 0xFF
            assertTrue(abs(e - a) <= 1, "channel at $shift: expected $e, got $a")
        }
    }

    @Test fun `primary colours white black and grey round trip`() {
        val colours = listOf(0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFFFF, 0xFF000000, 0xFF808080, 0x80336699)
        for (argb in colours) assertWithinOneStep(argb, Hsv.fromArgb(argb).toArgb())
    }

    @Test fun `known values convert both ways`() {
        assertEquals(0xFFFF0000, Hsv(0f, 1f, 1f).toArgb())
        assertEquals(0xFF00FF00, Hsv(120f, 1f, 1f).toArgb())
        assertEquals(0xFF0000FF, Hsv(240f, 1f, 1f).toArgb())
        assertEquals(0xFFFFFFFF, Hsv(0f, 0f, 1f).toArgb())
        assertEquals(0xFF000000, Hsv(200f, 0.5f, 0f).toArgb())
        val blue = Hsv.fromArgb(0xFF0000FF)
        assertEquals(240f, blue.h, 0.01f)
        assertEquals(1f, blue.s, 0.001f)
        assertEquals(1f, blue.v, 0.001f)
        assertEquals(1f, blue.a, 0.001f)
    }

    @Test fun `hue 360 equals hue 0`() {
        assertEquals(Hsv(0f, 1f, 1f).toArgb(), Hsv(360f, 1f, 1f).toArgb())
        assertEquals(Hsv(30f, 0.4f, 0.7f).toArgb(), Hsv(390f, 0.4f, 0.7f).toArgb())
    }

    @Test fun `every argb comes back within one step`() {
        var argb = 0xFF000000
        while (argb <= 0xFFFFFFFFL) {
            assertWithinOneStep(argb, Hsv.fromArgb(argb).toArgb())
            argb += 0x010305
        }
    }

    @Test fun `alpha is kept`() {
        assertEquals(0x80L, (Hsv(0f, 1f, 1f, a = 128 / 255f).toArgb() ushr 24) and 0xFF)
    }
}
