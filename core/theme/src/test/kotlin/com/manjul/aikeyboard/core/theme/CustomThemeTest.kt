/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.theme

import com.manjul.aikeyboard.core.theme.CustomTheme.Element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomThemeTest {
    private val base = CustomTheme.DEFAULT_BASE
    private val chosen = mapOf(
        Element.KEYS to 0xFFF4E8D0,
        Element.KEY_TEXT to 0xFF2A1B10,
        Element.BACKGROUND to 0xFF8A5A3C,
        Element.ACCENT to 0xFF1E88E5,
    )

    @Test fun `the 4 chosen colours appear in the spec`() {
        val spec = CustomTheme.spec(chosen, base)
        assertEquals(0xFFF4E8D0, spec.colors.key)
        assertEquals(0xFF2A1B10, spec.colors.text)
        assertEquals(0xFF8A5A3C, spec.colors.keyboardBackground)
        assertEquals(0xFF1E88E5, spec.colors.accent)
        val css = SnyggGenerator.stylesheet(spec, keyBorder = true)
        for (hex in listOf("#f4e8d0", "#2a1b10", "#8a5a3c", "#1e88e5")) assertTrue(hex in css, hex)
    }

    @Test fun `pressed is darker than keys for a light key colour`() {
        val spec = CustomTheme.spec(chosen + (Element.KEYS to 0xFFFFFFFF), base)
        assertTrue(relativeLuminance(spec.colors.keyPressed) < relativeLuminance(spec.colors.key))
    }

    @Test fun `pressed is lighter than keys for a dark key colour`() {
        val spec = CustomTheme.spec(chosen + (Element.KEYS to 0xFF202020), base)
        assertTrue(relativeLuminance(spec.colors.keyPressed) > relativeLuminance(spec.colors.key))
    }

    @Test fun `the popup is derived from the keys and differs from them`() {
        val spec = CustomTheme.spec(chosen, base)
        assertTrue(spec.colors.popup != spec.colors.key)
        assertEquals(spec.colors.popup, CustomTheme.spec(chosen + (Element.ACCENT to 0xFF00FF00), base).colors.popup)
    }

    @Test fun `a missing element keeps the base colour and the spec is never dynamic`() {
        val materialYou = ThemePresets.all.first { it.spec.dynamic != null }.spec
        val spec = CustomTheme.spec(mapOf(Element.ACCENT to 0xFF00FF00), materialYou)
        assertEquals(materialYou.colors.key, spec.colors.key)
        assertEquals(0xFF00FF00, spec.colors.accent)
        assertNull(spec.dynamic)
        SnyggGenerator.stylesheet(spec, keyBorder = false)
    }

    @Test fun `stored colours round trip and a bad list falls back to the base`() {
        assertEquals(chosen, CustomTheme.fromStored(CustomTheme.toStored(chosen), base))
        assertEquals(listOf(0xFFF4E8D0, 0xFF2A1B10, 0xFF8A5A3C, 0xFF1E88E5), CustomTheme.toStored(chosen))
        val defaults = CustomTheme.fromStored(emptyList(), base)
        assertEquals(base.colors.key, defaults[Element.KEYS])
        assertEquals(base.colors.text, defaults[Element.KEY_TEXT])
        assertEquals(base.colors.keyboardBackground, defaults[Element.BACKGROUND])
        assertEquals(base.colors.accent, defaults[Element.ACCENT])
        assertEquals(defaults, CustomTheme.fromStored(listOf(1L, 2L, 3L), base))
        assertEquals(defaults, CustomTheme.fromStored(listOf(1L, 2L, 3L, 0x1FFFFFFFFL), base))
    }

    @Test fun `the custom extension id is not the preset extension`() {
        assertEquals("com.manjul.aikeyboard.themes.custom", CustomTheme.EXTENSION_ID)
        assertTrue(CustomTheme.EXTENSION_ID != SnyggGenerator.EXTENSION_ID)
    }
}
