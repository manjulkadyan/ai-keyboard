/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemePresetsTest {
    private val all = ThemePresets.all

    @Test fun `twelve presets with unique snake_case ids`() {
        assertEquals(12, all.size)
        assertEquals(all.size, all.map { it.id }.toSet().size)
        val snake = Regex("^[a-z][a-z0-9]*(_[a-z0-9]+)*$")
        for (preset in all) assertTrue(snake.matches(preset.id), preset.id)
    }

    @Test fun `the plan's ids, four of them premium`() {
        assertEquals(
            listOf("light", "dark", "amoled", "material_you_light", "material_you_dark", "ocean", "forest", "slate"),
            all.filterNot { it.premium }.map { it.id },
        )
        assertEquals(listOf("sunset", "rose", "midnight", "sand"), all.filter { it.premium }.map { it.id })
    }

    @Test fun `only the Material You presets are dynamic and need API 31`() {
        val dynamic = all.filter { it.spec.dynamic != null }
        assertEquals(listOf("material_you_light", "material_you_dark"), dynamic.map { it.id })
        assertEquals(DynamicScheme.LIGHT, dynamic[0].spec.dynamic)
        assertEquals(DynamicScheme.DARK, dynamic[1].spec.dynamic)
        for (preset in all) assertEquals(if (preset in dynamic) 31 else 26, preset.minSdk, preset.id)
    }

    @Test fun `text to key contrast is at least 4_5 for every non-dynamic preset`() {
        for (preset in all.filter { it.spec.dynamic == null }) {
            val c = preset.spec.colors
            val ratio = contrastRatio(c.text, c.key)
            assertTrue(ratio >= 4.5, "${preset.id}: text on key is $ratio")
        }
    }

    @Test fun `every preset generates both stylesheets`() {
        for (preset in all) {
            SnyggGenerator.stylesheet(preset.spec, keyBorder = true)
            SnyggGenerator.stylesheet(preset.spec, keyBorder = false)
        }
    }
}
