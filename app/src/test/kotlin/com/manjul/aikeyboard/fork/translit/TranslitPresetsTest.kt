/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translit

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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

class TranslitPresetsTest {
    private fun load(ext: String): List<SubtypePreset> {
        val file = listOf(File("src/main"), File("app/src/main"))
            .map { File(it, "assets/ime/keyboard/$ext/extension.json") }
            .firstOrNull { it.isFile } ?: fail("extension.json not found")
        val array = Json.parseToJsonElement(file.readText()).jsonObject.getValue("subtypePresets").jsonArray
        return SubtypeJsonConfig.decodeFromJsonElement(ListSerializer(SubtypePreset.serializer()), array)
    }

    private val presets by lazy { load("org.aikeyboard.layouts") + load("org.florisboard.localization") }

    @Test fun `the Roman presets use qwerty and the translit provider`() {
        for (tag in listOf("ur-PK-roman", "hi-IN-roman")) {
            val preset = assertNotNull(presets.find { it.locale == FlorisLocale.fromTag(tag) }, tag)
            assertEquals("org.florisboard.layouts:qwerty", preset.preferred.characters.toString())
            assertEquals(TranslitSuggestionProvider.ProviderId, preset.nlpProviders.suggestion)
            // The latin provider would load ur.sdict into LatinSuggester on preload, so no slot may name it.
            assertEquals("org.florisboard.nlp.providers.fallback", preset.nlpProviders.spelling)
            preset.nlpProviders.forEach { _, id ->
                assertNotEquals("org.florisboard.nlp.providers.latin", id, tag)
            }
        }
    }

    @Test fun `the roman tag splits into language, country and variant`() {
        val base = FlorisLocale.fromTag("ur-PK-roman").base
        assertEquals("ur", base.language)
        assertEquals("PK", base.country)
        assertEquals("roman", base.variant)
    }
}
