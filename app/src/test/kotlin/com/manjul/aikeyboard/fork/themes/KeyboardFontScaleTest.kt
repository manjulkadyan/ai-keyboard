/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.themes

import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class KeyboardFontScaleTest {
    @Test
    fun `largest system font scale is capped`() {
        assertEquals(KeyboardFontScale.MAX_FONT_SCALE, KeyboardFontScale.cap(2.0f))
        val d = KeyboardFontScale.capped(Density(2.5f, 2.0f))
        assertEquals(KeyboardFontScale.MAX_FONT_SCALE, d.fontScale)
        assertEquals(2.5f, d.density)
    }

    @Test
    fun `small font scale is untouched`() {
        assertEquals(0.85f, KeyboardFontScale.cap(0.85f))
        val d = Density(2.5f, 0.85f)
        assertSame(d, KeyboardFontScale.capped(d))
    }
}
