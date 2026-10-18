/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.subtypes

import com.manjul.aikeyboard.ime.core.SubtypeJsonConfig
import com.manjul.aikeyboard.ime.core.SubtypePreset
import com.manjul.aikeyboard.lib.FlorisLocale
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

/**
 * `SubtypeManager` needs `FlorisApplication` (keyboard manager, native library), so it is not constructed here.
 * `ensureDefaultSubtypes` is a thin loop over [missingSubtypeTags] and the preset lookup, which are tested directly:
 * the decision as a pure function, and the presets by decoding the shipped localization extension with the same
 * rules `getSubtypePresetForLocale` applies (exact locale, then same language).
 */
class DefaultSubtypesTest {
    private val presets: List<SubtypePreset> by lazy {
        val file = listOf(File("src/main"), File("app/src/main"))
            .map { File(it, "assets/ime/keyboard/org.florisboard.localization/extension.json") }
            .firstOrNull { it.isFile } ?: fail("extension.json not found")
        val array = Json.parseToJsonElement(file.readText()).jsonObject.getValue("subtypePresets").jsonArray
        SubtypeJsonConfig.decodeFromJsonElement(ListSerializer(SubtypePreset.serializer()), array)
    }

    private fun presetFor(tag: String): SubtypePreset? {
        val locale = FlorisLocale.fromTag(tag)
        return presets.find { it.locale == locale } ?: presets.find { it.locale.language == locale.language }
    }

    private fun locales(vararg tags: String) = tags.map { FlorisLocale.fromTag(it) }

    @Test
    fun `empty list gets the requested tags in order`() {
        assertEquals(listOf("en-US", "ur-PK"), missingSubtypeTags(emptyList(), listOf("en-US", "ur-PK")))
    }

    @Test
    fun `second call adds nothing`() {
        assertEquals(emptyList(), missingSubtypeTags(locales("en-US", "ur-PK"), listOf("en-US", "ur-PK")))
    }

    @Test
    fun `only missing tags are added and user subtypes are untouched`() {
        assertEquals(
            listOf("es-ES", "hi-IN"),
            missingSubtypeTags(locales("fr-FR", "en-US"), listOf("en-US", "es-ES", "fr-FR", "hi-IN")),
        )
    }

    @Test
    fun `duplicate tags are added once`() {
        assertEquals(listOf("id"), missingSubtypeTags(emptyList(), listOf("id", "id")))
    }

    @Test
    fun `every MVP tag has a preset`() {
        for (tag in MVP_LANGUAGE_TAGS) assertNotNull(presetFor(tag), "no preset for $tag")
    }

    @Test
    fun `urdu preset uses the phonetic layout`() {
        assertEquals("org.florisboard.layouts:urdu_phonetic", presetFor("ur-PK")!!.preferred.characters.toString())
    }
}
