/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.theme

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SnyggGeneratorTest {
    private fun preset(id: String) = ThemePresets.all.single { it.id == id }

    private fun parse(json: String) = Json.parseToJsonElement(json).jsonObject

    @Test fun `light with a border matches the golden file`() {
        val golden = checkNotNull(javaClass.getResource("/golden/light.json")) { "golden/light.json missing" }.readText()
        assertEquals(golden, SnyggGenerator.stylesheet(preset("light").spec, keyBorder = true))
    }

    @Test fun `bordered and borderless differ only in key border and shadow properties`() {
        val borderProps = setOf("border-color", "border-width", "shadow-elevation")
        for (p in ThemePresets.all) {
            val bordered = parse(SnyggGenerator.stylesheet(p.spec, keyBorder = true))
            val borderless = parse(SnyggGenerator.stylesheet(p.spec, keyBorder = false))
            assertEquals(bordered.keys, borderless.keys, p.id)
            var differs = false
            for (rule in bordered.keys) {
                val a = bordered[rule] as? JsonObject ?: continue
                val b = borderless.getValue(rule).jsonObject
                val changed = (a.keys + b.keys).filter { a[it] != b[it] }
                if (changed.isEmpty()) continue
                differs = true
                assertEquals("key", rule, "${p.id}: $rule differs")
                assertTrue(borderProps.containsAll(changed), "${p.id}: key differs in $changed")
            }
            assertTrue(differs, "${p.id}: bordered and borderless are the same")
            val key = bordered.getValue("key").jsonObject
            assertTrue(borderProps.all { it in key }, p.id)
            assertTrue(borderProps.none { it in borderless.getValue("key").jsonObject }, p.id)
        }
    }

    @Test fun `material_you_dark holds dynamic dark colour values only`() {
        val defines = parse(SnyggGenerator.stylesheet(preset("material_you_dark").spec, keyBorder = true))
            .getValue("@defines").jsonObject
        val colours = defines.filterKeys { !it.startsWith("--shape") }.values.map { it.jsonPrimitive.content }
        assertTrue(colours.isNotEmpty())
        val syntax = Regex("""^dynamic-dark-color\([a-zA-Z]+\)$""")
        for (value in colours) assertTrue(syntax.matches(value), value)
        assertEquals("dynamic-dark-color(onSurface)", defines.getValue("--text").jsonPrimitive.content)
    }

    @Test fun `material_you_light uses the light scheme`() {
        val defines = parse(SnyggGenerator.stylesheet(preset("material_you_light").spec, keyBorder = false))
            .getValue("@defines").jsonObject
        assertEquals("dynamic-light-color(primary)", defines.getValue("--accent").jsonPrimitive.content)
    }

    @Test fun `static presets hold no dynamic colour`() {
        val json = SnyggGenerator.stylesheet(preset("ocean").spec, keyBorder = true)
        assertFalse("dynamic-" in json)
        assertTrue("\"--key\": \"#0f4c5c\"" in json)
    }

    @Test fun `MONO font and key gap reach the key rule`() {
        val spec = preset("light").spec.copy(font = ThemeFont.MONO, keyGapDp = 3)
        val key = parse(SnyggGenerator.stylesheet(spec, keyBorder = true)).getValue("key").jsonObject
        assertEquals("monospace", key.getValue("font-family").jsonPrimitive.content)
        assertEquals("3dp", key.getValue("margin").jsonPrimitive.content)
    }

    @Test fun `ROUNDED font throws until a rounded face is bundled`() {
        val spec = preset("light").spec.copy(font = ThemeFont.ROUNDED)
        assertFailsWith<UnsupportedOperationException> { SnyggGenerator.stylesheet(spec, keyBorder = true) }
    }

    @Test fun `corner radius sets the shapes`() {
        val defines = parse(SnyggGenerator.stylesheet(preset("light").spec.copy(cornerRadiusDp = 9), true))
            .getValue("@defines").jsonObject
        assertEquals("rounded-corner(9dp, 9dp, 9dp, 9dp)", defines.getValue("--shape").jsonPrimitive.content)
    }

    @Test fun `background opacity becomes the window alpha`() {
        val spec = preset("light").spec.copy(background = Background(BackgroundType.COLOR, "", 0.5f, 0))
        val defines = parse(SnyggGenerator.stylesheet(spec, keyBorder = true)).getValue("@defines").jsonObject
        assertEquals("#dcdfe480", defines.getValue("--keyboard-background").jsonPrimitive.content)
    }

    @Test fun `IMAGE and GRADIENT backgrounds throw v1`() {
        for (type in listOf(BackgroundType.IMAGE, BackgroundType.GRADIENT)) {
            val spec = preset("light").spec.copy(background = Background(type, "bg.png", 1f, 4))
            val e = assertFailsWith<UnsupportedOperationException> { SnyggGenerator.stylesheet(spec, keyBorder = true) }
            assertEquals("v1", e.message)
        }
    }

    @Test fun `extension lists both stylesheets of every preset`() {
        val ext = parse(SnyggGenerator.extension(ThemePresets.all))
        assertEquals("ime.extension.theme", ext.getValue("$").jsonPrimitive.content)
        assertEquals(SnyggGenerator.EXTENSION_ID, ext.getValue("meta").jsonObject.getValue("id").jsonPrimitive.content)
        val themes = ext.getValue("themes").jsonArray.map { it.jsonObject }
        assertEquals(24, themes.size)
        assertEquals(
            ThemePresets.all.flatMap { listOf(it.id, "${it.id}_borderless") },
            themes.map { it.getValue("id").jsonPrimitive.content },
        )
        val night = themes.associate { it.getValue("id").jsonPrimitive.content to it.getValue("isNight").jsonPrimitive.boolean }
        assertFalse(night.getValue("light"))
        assertTrue(night.getValue("dark_borderless"))
        assertTrue(night.getValue("material_you_dark"))
        assertFalse(night.getValue("sand"))
        assertEquals("Material You Dark (Borderless)", themes[9].getValue("label").jsonPrimitive.content)
    }
}
