/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ThemeSpecTest {
    private val colors = ThemeColors(
        key = 0xFFFFFFFF, keyPressed = 0xFFEEEEEE, keyboardBackground = 0xFFDDDDDD,
        text = 0xFF000000, accent = 0xFF3366CC, popup = 0xFFF5F5F5,
    )

    private fun spec(cornerRadiusDp: Int = 6, keyGapDp: Int = 0, background: Background = Background.SOLID) =
        ThemeSpec(colors, cornerRadiusDp, keyGapDp, ThemeFont.DEFAULT, background, dynamic = null)

    @Test fun `opacity above 1 throws`() {
        assertFailsWith<IllegalArgumentException> { Background(BackgroundType.COLOR, "", opacity = 1.1f, blurRadius = 0) }
    }

    @Test fun `negative opacity throws`() {
        assertFailsWith<IllegalArgumentException> { Background(BackgroundType.COLOR, "", opacity = -0.1f, blurRadius = 0) }
    }

    @Test fun `blur 26 throws and 25 is accepted`() {
        assertFailsWith<IllegalArgumentException> { Background(BackgroundType.IMAGE, "", opacity = 1f, blurRadius = 26) }
        assertEquals(25, Background(BackgroundType.IMAGE, "", opacity = 1f, blurRadius = 25).blurRadius)
    }

    @Test fun `negative blur throws`() {
        assertFailsWith<IllegalArgumentException> { Background(BackgroundType.IMAGE, "", opacity = 1f, blurRadius = -1) }
    }

    @Test fun `negative corner radius throws`() {
        assertFailsWith<IllegalArgumentException> { spec(cornerRadiusDp = -1) }
    }

    @Test fun `negative key gap throws`() {
        assertFailsWith<IllegalArgumentException> { spec(keyGapDp = -1) }
    }

    @Test fun `colour outside ARGB range throws`() {
        assertFailsWith<IllegalArgumentException> { colors.copy(text = 0x1_0000_0000L) }
        assertFailsWith<IllegalArgumentException> { colors.copy(key = -1L) }
    }

    @Test fun `contrast ratio matches WCAG reference values`() {
        assertEquals(21.0, contrastRatio(0xFF000000, 0xFFFFFFFF), 1e-9)
        assertEquals(1.0, contrastRatio(0xFF777777, 0xFF777777), 1e-9)
        // WCAG reference: #767676 on white is 4.54:1.
        assertEquals(4.54, contrastRatio(0xFF767676, 0xFFFFFFFF), 0.01)
        assertEquals(contrastRatio(0xFF123456, 0xFFABCDEF), contrastRatio(0xFFABCDEF, 0xFF123456), 1e-12)
    }
}
