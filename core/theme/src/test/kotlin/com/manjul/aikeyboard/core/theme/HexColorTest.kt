/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HexColorTest {
    @Test fun `six and eight digit values parse in either case`() {
        assertEquals(0xFFFF8800, HexColor.parse("#ff8800"))
        assertEquals(0xFFFF8800, HexColor.parse("#FF8800"))
        assertEquals(0x80FF8800, HexColor.parse("#80FF8800"))
        assertEquals(0x80FF8800, HexColor.parse("#80ff8800"))
    }

    @Test fun `invalid values give null`() {
        for (text in listOf("ff8800", "#ff88", "#gg0000", "", "#", "#ff88000", "#ff880000ff", "##ff8800", "#ff 800")) {
            assertNull(HexColor.parse(text), text)
        }
    }

    @Test fun `format round trips`() {
        assertEquals("#FF8800", HexColor.format(0xFFFF8800, withAlpha = false))
        assertEquals("#80FF8800", HexColor.format(0x80FF8800, withAlpha = true))
        assertEquals("#000000", HexColor.format(0xFF000000, withAlpha = false))
        for (argb in listOf(0xFFFF8800, 0x00000000, 0x80123456, 0xFFFFFFFF)) {
            assertEquals(argb, HexColor.parse(HexColor.format(argb, withAlpha = true)))
            assertEquals(argb or 0xFF000000, HexColor.parse(HexColor.format(argb, withAlpha = false)))
        }
    }
}
