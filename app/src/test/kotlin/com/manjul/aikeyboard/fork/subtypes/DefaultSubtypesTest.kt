/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.subtypes

import com.manjul.aikeyboard.ime.core.Subtype
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

    private fun seed(current: List<Subtype>, vararg tags: String, baseId: Long = 1000) =
        buildSeededList(current, presets, tags.toList(), baseId)

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
        for (tag in MVP_LANGUAGE_TAGS) assertNotNull(findPreset(presets, FlorisLocale.fromTag(tag)), "no preset for $tag")
    }

    @Test
    fun `urdu preset uses the phonetic layout`() {
        assertEquals("org.florisboard.layouts:urdu_phonetic", seed(emptyList(), "ur-PK").single().layoutMap.characters.toString())
    }

    @Test
    fun `seeding twice yields two subtypes with unique ids in tag order`() {
        val first = seed(emptyList(), "en-US", "ur-PK")
        val second = seed(first, "en-US", "ur-PK", baseId = 5000)
        assertEquals(second, first)
        assertEquals(listOf("en-US", "ur-PK"), first.map { it.primaryLocale.languageTag() })
        assertEquals(2, first.map { it.id }.toSet().size)
    }

    @Test
    fun `seeding keeps existing subtypes and appends only the missing ones`() {
        val existing = seed(emptyList(), "fr-FR")
        val result = seed(existing, "en-US", "fr-FR", "id", baseId = 7000)
        assertEquals(listOf("fr-FR", "en-US", "id"), result.map { it.primaryLocale.languageTag() })
        assertEquals(existing.single(), result.first())
    }

    @Test
    fun `tags without a preset are skipped`() {
        assertEquals(emptyList(), seed(emptyList(), "xx-YY"))
    }
}
