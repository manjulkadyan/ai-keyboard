/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.layouts

import com.manjul.aikeyboard.ime.keyboard.KeyboardExtension
import com.manjul.aikeyboard.ime.keyboard.LayoutArrangement
import com.manjul.aikeyboard.ime.popup.PopupMapping
import com.manjul.aikeyboard.lib.FlorisLocale
import com.manjul.aikeyboard.lib.ext.ExtensionJsonConfig
import com.manjul.aikeyboard.lib.io.loadJsonAsset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Checks that every letter, sign and digit of a script can be typed on the characters keyboard of a subtype, the way
 * `LayoutManager` assembles it from the shipped JSON: the characters layout (every case/shift/variation branch and
 * inline popups), its modifier layout (`modifier` from the layout meta, else `default`), the locale's popup mapping
 * looked up by key label and group (`~left`/`~right`/`~enter`, falling back to the `default` mapping), and the
 * preset's number-row layout, which is also the top row of the symbols layer (our digit choice: native or western).
 */
class ScriptCoverageTest {
    private val keyboardDir: File by lazy {
        listOf(File("src/main"), File("app/src/main"))
            .map { File(it, "assets/ime/keyboard") }
            .firstOrNull { it.isDirectory } ?: fail("assets/ime/keyboard not found")
    }

    private fun readJson(file: File): JsonElement {
        if (!file.isFile) fail("missing ${file.relativeTo(keyboardDir)}")
        return Json.parseToJsonElement(file.readText())
    }

    private fun manifest(extensionId: String) = readJson(File(keyboardDir, "$extensionId/extension.json")).jsonObject

    private fun split(name: String) = name.substringBefore(':') to name.substringAfter(':')

    private fun layoutMeta(type: String, name: String): JsonObject {
        val (ext, id) = split(name)
        return manifest(ext)["layouts"]?.jsonObject?.get(type)?.jsonArray
            ?.map { it.jsonObject }
            ?.firstOrNull { it.string("id") == id } ?: fail("$name is not declared in $ext/extension.json")
    }

    private fun layoutFile(type: String, name: String): File {
        val (ext, id) = split(name)
        return File(keyboardDir, "$ext/layouts/$type/$id.json")
    }

    private fun popupMappingFile(name: String): File {
        val (ext, id) = split(name)
        return File(keyboardDir, "$ext/popupMappings/$id.json")
    }

    private fun preset(languageTag: String): JsonObject =
        manifest("org.florisboard.localization").getValue("subtypePresets").jsonArray
            .map { it.jsonObject }
            .firstOrNull { it.string("languageTag") == languageTag } ?: fail("no preset for $languageTag")

    private fun JsonObject.string(key: String) = get(key)?.jsonPrimitive?.content

    /** Collects code points of typeable keys under [element]; layout keys (not their popups) go to [keys]. */
    private fun collect(element: JsonElement, out: MutableSet<Int>, keys: MutableList<JsonObject>?) {
        when (element) {
            is JsonArray -> element.forEach { collect(it, out, keys) }
            is JsonObject -> {
                val type = element.string("type")
                val isText = element.containsKey("code") || element.containsKey("codePoints")
                if (isText && (type == null || type == "character" || type == "numeric")) {
                    element["code"]?.jsonPrimitive?.int?.takeIf { it > 0 }?.let { out.add(it) }
                    element["codePoints"]?.jsonArray?.forEach { out.add(it.jsonPrimitive.int) }
                    keys?.add(element)
                }
                for ((key, value) in element) {
                    when (key) {
                        "popup" -> collect(value, out, null)
                        "code", "codePoints", "label", "type", "groupId", "$" -> Unit
                        else -> collect(value, out, keys)
                    }
                }
            }
            else -> Unit
        }
    }

    private fun reachable(characters: String, languageTag: String): Set<Int> {
        val preset = preset(languageTag)
        val modifier = layoutMeta("characters", characters).string("modifier") ?: "org.florisboard.layouts:default"
        val numericRow = preset["preferred"]?.jsonObject?.string("numericRow") ?: "org.florisboard.layouts:western_arabic"

        val out = mutableSetOf<Int>()
        val keys = mutableListOf<JsonObject>()
        collect(readJson(layoutFile("characters", characters)), out, keys)
        collect(readJson(layoutFile("charactersMod", modifier)), out, keys)
        collect(readJson(layoutFile("numericRow", numericRow)), out, null)

        val localeAll = readJson(popupMappingFile(preset.string("popupMapping") ?: fail("$languageTag: no popupMapping")))
            .jsonObject["all"]?.jsonObject ?: JsonObject(emptyMap())
        val defaultAll = readJson(popupMappingFile("org.florisboard.localization:default"))
            .jsonObject["all"]?.jsonObject ?: JsonObject(emptyMap())
        fun lookup(label: String) = localeAll[label] ?: defaultAll[label]
        for (key in keys) {
            val label = key.string("label") ?: continue
            val group = when (key["groupId"]?.jsonPrimitive?.int) {
                1 -> "~left"
                2 -> "~right"
                3 -> "~enter"
                else -> null
            }
            (group?.let(::lookup) ?: lookup(label))?.let { collect(it, out, null) }
            if (group != null) lookup(label)?.let { collect(it, out, null) }
        }
        return out
    }

    private fun assertCovers(required: Set<Int>, characters: String, languageTag: String) {
        val missing = required - reachable(characters, languageTag)
        assertTrue(
            missing.isEmpty(),
            "$characters ($languageTag) cannot type: " +
                missing.sorted().joinToString { "U+%04X %s".format(it, String(Character.toChars(it))) },
        )
    }

    private fun range(from: Int, to: Int) = (from..to).toSet()

    // ا آ ب پ ت ٹ ث ج چ ح خ د ڈ ذ ر ڑ ز ژ س ش ص ض ط ظ ع غ ف ق ک گ ل م ن ں و ہ ھ ء ی ے ۓ ۂ ئ ؤ
    private val urduLetters = setOf(
        0x0627, 0x0622, 0x0628, 0x067E, 0x062A, 0x0679, 0x062B, 0x062C, 0x0686, 0x062D, 0x062E, 0x062F, 0x0688, 0x0630,
        0x0631, 0x0691, 0x0632, 0x0698, 0x0633, 0x0634, 0x0635, 0x0636, 0x0637, 0x0638, 0x0639, 0x063A, 0x0641, 0x0642,
        0x06A9, 0x06AF, 0x0644, 0x0645, 0x0646, 0x06BA, 0x0648, 0x06C1, 0x06BE, 0x0621, 0x06CC, 0x06D2, 0x06D3, 0x06C2,
        0x0626, 0x0624,
    )

    // ، ؟ ۔
    private val urduPunctuation = setOf(0x060C, 0x061F, 0x06D4)

    // ۰–۹
    private val urduDigits = range(0x06F0, 0x06F9)

    private val urdu = urduLetters + urduPunctuation + urduDigits

    // vowels अ–औ, matras ा–ौ, consonants क–ह, ं ः ँ ् ़ ।, digits ०–९
    private val hindi = range(0x0905, 0x0914) + range(0x093E, 0x094C) + range(0x0915, 0x0939) +
        setOf(0x0902, 0x0903, 0x0901, 0x094D, 0x093C, 0x0964) + range(0x0966, 0x096F)

    // ا–غ and ف–ي (U+063B–U+0640 are not Arabic-alphabet letters: extended letters and tatweel), ة ى ء أ إ آ ؤ ئ,
    // ، ؟ ؛, ٠–٩
    private val arabic = range(0x0627, 0x063A) + range(0x0641, 0x064A) +
        setOf(0x0629, 0x0649, 0x0621, 0x0623, 0x0625, 0x0622, 0x0624, 0x0626) +
        setOf(0x060C, 0x061F, 0x061B) + range(0x0660, 0x0669)

    @Test
    fun `urdu phonetic covers the urdu alphabet, punctuation and digits`() =
        assertCovers(urdu, "org.florisboard.layouts:urdu_phonetic", "ur-PK")

    @Test
    fun `urdu standard covers the urdu alphabet, punctuation and digits`() =
        assertCovers(urdu, "org.aikeyboard.layouts:urdu_standard", "ur-PK")

    @Test
    fun `hindi inscript covers the hindi alphabet, signs and digits`() =
        assertCovers(hindi, "org.florisboard.layouts:hindi_in", "hi-IN")

    @Test
    fun `hindi phonetic covers the hindi alphabet, signs and digits`() =
        assertCovers(hindi, "org.aikeyboard.layouts:hindi_phonetic", "hi-IN")

    @Test
    fun `arabic covers the arabic alphabet, punctuation and digits`() =
        assertCovers(arabic, "org.florisboard.layouts:arabic", "ar")

    @Test
    fun `new layouts declare direction and modifier like upstream`() {
        val urdu = layoutMeta("characters", "org.aikeyboard.layouts:urdu_standard")
        assertEquals("rtl", urdu.string("direction"))
        assertEquals("org.florisboard.layouts:arabic", urdu.string("modifier"))
        val hindi = layoutMeta("characters", "org.aikeyboard.layouts:hindi_phonetic")
        assertEquals("ltr", hindi.string("direction"))
        assertEquals(null, hindi.string("modifier"))
    }

    @Test
    fun `new extension and layouts decode with the app serializers`() {
        val ext = ExtensionJsonConfig.decodeFromString(
            KeyboardExtension.serializer(),
            File(keyboardDir, "org.aikeyboard.layouts/extension.json").readText(),
        )
        assertEquals("org.aikeyboard.layouts", ext.meta.id)
        assertEquals(
            listOf("urdu_standard", "hindi_phonetic"),
            ext.layouts.getValue("characters").map { it.id },
        )
        for (name in listOf("org.aikeyboard.layouts:urdu_standard", "org.aikeyboard.layouts:hindi_phonetic")) {
            loadJsonAsset<LayoutArrangement>(layoutFile("characters", name).readText()).getOrThrow()
        }
        for (name in listOf("org.florisboard.localization:ur-PK", "org.florisboard.localization:hi-IN")) {
            loadJsonAsset<PopupMapping>(popupMappingFile(name).readText()).getOrThrow()
        }
    }

    @Test
    fun `preset default layouts are unchanged`() {
        assertEquals("org.florisboard.layouts:urdu_phonetic", preset("ur-PK")["preferred"]?.jsonObject?.string("characters"))
        assertEquals("org.florisboard.layouts:hindi_in", preset("hi-IN")["preferred"]?.jsonObject?.string("characters"))
    }

    @Test
    fun `urdu has no auto-capitalization so the shift layer is only reached on purpose`() {
        assertFalse(FlorisLocale.fromTag("ur-PK").supportsCapitalization)
    }
}
