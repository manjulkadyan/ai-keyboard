/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.branding

import java.io.File
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * TRD §2.1 "Package and branding": no user-visible string may name the upstream product. Scans the text
 * of every `<string>` and `<plurals>` in the `strings*.xml` files of all `app/src/main/res/values*` directories
 * (attributes such as `comment="..."` and XML comments are not user-visible and are ignored).
 *
 * The match is case-insensitive "floris" (covers "FlorisBoard", "Florisboard", "florisboardu",
 * misspellings like "FlorisBorad" and "TastieraFloris") plus the Cyrillic and Persian transliterations
 * found in upstream translations.
 */
class UpstreamBrandingStringsTest {
    /** Entries that may name the upstream project, keyed as "<values dir>/<file>#<name>". */
    private val allowList = mapOf(
        "values/strings_fork.xml#about__based_on_florisboard" to
            "Apache-2.0 §4(d) attribution line on the About screen",
        "values/strings_dont_translate.xml#florisboard__internal_key_codes_url" to
            "Theme editor developer reference: code search for upstream KeyCode.kt",
        "values/strings_dont_translate.xml#florisboard__spell_checker_wiki_url" to
            "Unused upstream resource (no call site); kept to avoid an upstream merge conflict",
        "values/strings_dont_translate.xml#florisboard__theme_editor_wiki_url" to
            "Unused upstream resource (no call site); kept to avoid an upstream merge conflict",
    )

    private val brandPattern = Regex("floris|флорис|فلوریس|فلوريس", RegexOption.IGNORE_CASE)

    private val resDir: File = listOf(File("src/main/res"), File("app/src/main/res"))
        .firstOrNull { File(it, "values/strings.xml").isFile }
        ?: fail("Cannot locate app/src/main/res from working dir ${File(".").absolutePath}")

    data class Hit(val key: String, val text: String)

    /** Returns the `<string>`/`<plurals>` entries of [xml] whose text matches [brandPattern]. */
    private fun findBranding(label: String, xml: InputSource): List<Hit> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val root = factory.newDocumentBuilder().parse(xml).documentElement
        val hits = mutableListOf<Hit>()
        for (tag in listOf("string", "plurals")) {
            val nodes = root.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val element = nodes.item(i) as Element
                // textContent includes child elements (<item>, <xliff:g>) but not comments or attributes.
                val text = element.textContent
                if (brandPattern.containsMatchIn(text)) {
                    hits += Hit("$label#${element.getAttribute("name")}", text.trim().replace(Regex("\\s+"), " "))
                }
            }
        }
        return hits
    }

    private fun scanResources(): List<Hit> {
        val files = resDir.listFiles { f -> f.isDirectory && f.name.startsWith("values") }.orEmpty()
            .flatMap { dir -> dir.listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }.orEmpty().toList() }
            .sortedBy { it.path }
        assertTrue(files.size > 10, "Expected the default and translated strings files under $resDir, found ${files.size}")
        return files.flatMap { f -> f.inputStream().use { findBranding("${f.parentFile.name}/${f.name}", InputSource(it)) } }
    }

    @Test
    fun `no user-visible string names the upstream product`() {
        val offending = scanResources().filter { it.key !in allowList }
        assertTrue(
            offending.isEmpty(),
            "Upstream product name in user-visible strings (use {app_name} or \"AI Keyboard\", " +
                "see docs/FORK_PATCHES.md S0-branding):\n" +
                offending.joinToString("\n") { "  ${it.key}: ${it.text.take(120)}" },
        )
    }

    @Test
    fun `allow-list entries still exist and still need the exemption`() {
        val hitKeys = scanResources().map { it.key }.toSet()
        val stale = allowList.keys - hitKeys
        assertTrue(stale.isEmpty(), "Remove stale allow-list entries: $stale")
    }

    @Test
    fun `detector flags the upstream spellings and ignores comments and attributes`() {
        // Upstream v0.5.2 text that the first rebrand pass missed, plus things that must not be flagged.
        val fixture = """
            <resources>
                <!-- FlorisBoard in a comment is fine -->
                <string name="a" comment="Shown when FlorisBoard is not enabled">{app_name} is not enabled</string>
                <string name="setup__grant_notification_permission__description">As of Android 13+, apps must ask for permission to
                    send notifications. In Florisboard, this is only used to open a crash report screen.</string>
                <string name="crash_dialog__title">Zpráva o chybě florisboardu</string>
                <string name="ckb">FlorisBorad ڕاپۆرتی هەڵەی</string>
                <string name="sr">ФлорисБоард</string>
                <string name="fa">گزارش خطای فلوریس بورد</string>
                <plurals name="p"><item quantity="one">One FLORISBOARD item</item></plurals>
                <string name="ok">AI Keyboard</string>
            </resources>
        """.trimIndent()
        val keys = findBranding("fixture", InputSource(StringReader(fixture))).map { it.key.substringAfter('#') }
        assertEquals(
            listOf("setup__grant_notification_permission__description", "crash_dialog__title", "ckb", "sr", "fa", "p"),
            keys,
        )
    }
}
