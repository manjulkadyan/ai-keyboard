/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ColorSamplerTest {
    @Test fun `a 5 x 5 patch of one colour gives that colour`() {
        val pixels = IntArray(25) { 0xFF336699.toInt() }
        assertEquals(0xFF336699, ColorSampler.sample(pixels, width = 5, x = 2, y = 2))
    }

    @Test fun `a two colour patch gives the average`() {
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        // Left two columns red, right three blue: 10 red and 15 blue pixels in the 5 x 5 window.
        val pixels = IntArray(25) { i -> if (i % 5 < 2) red else blue }
        // red 255 * 10 / 25 = 102 (0x66), blue 255 * 15 / 25 = 153 (0x99)
        assertEquals(0xFF660099, ColorSampler.sample(pixels, width = 5, x = 2, y = 2))
        // Two pixels: 127.5 rounds up.
        assertEquals(0xFF800080, ColorSampler.sample(intArrayOf(red, blue), width = 2, x = 0, y = 0, radius = 1))
    }

    @Test fun `an edge pixel clamps the window to the image`() {
        // 3 x 3 black with a white top-left corner.
        val pixels = IntArray(9) { 0xFF000000.toInt() }.also { it[0] = 0xFFFFFFFF.toInt() }
        // The window at (0, 0) with radius 2 is the whole image: 255 / 9 = 28 (0x1C).
        assertEquals(0xFF1C1C1C, ColorSampler.sample(pixels, width = 3, x = 0, y = 0))
        assertEquals(0xFFFFFFFF, ColorSampler.sample(pixels, width = 3, x = 0, y = 0, radius = 0))
        // A point outside the image clamps to the nearest edge pixel.
        assertEquals(0xFFFFFFFF, ColorSampler.sample(pixels, width = 3, x = -4, y = -1, radius = 0))
        assertEquals(0xFF000000, ColorSampler.sample(pixels, width = 3, x = 9, y = 9, radius = 0))
    }

    @Test fun `a bad image throws`() {
        assertFailsWith<IllegalArgumentException> { ColorSampler.sample(IntArray(0), width = 1, x = 0, y = 0) }
        assertFailsWith<IllegalArgumentException> { ColorSampler.sample(IntArray(5), width = 2, x = 0, y = 0) }
        assertFailsWith<IllegalArgumentException> {
            ColorSampler.sample(IntArray(4), width = 2, x = 0, y = 0, radius = -1)
        }
    }
}
