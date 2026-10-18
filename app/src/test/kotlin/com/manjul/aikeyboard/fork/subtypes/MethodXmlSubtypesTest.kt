/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.subtypes

import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class MethodXmlSubtypesTest {
    private val androidNs = "http://schemas.android.com/apk/res/android"

    private val mainDir: File = listOf(File("src/main"), File("app/src/main"))
        .firstOrNull { File(it, "AndroidManifest.xml").isFile }
        ?: fail("Cannot locate app/src/main from working dir ${File(".").absolutePath}")

    private fun subtypes(): List<Element> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = factory.newDocumentBuilder().parse(File(mainDir, "res/xml/method.xml"))
        val nodes = doc.getElementsByTagName("subtype")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.attr(name: String) = getAttributeNS(androidNs, name)

    @Test
    fun `declares exactly the 8 MVP language tags in order`() {
        assertEquals(MVP_LANGUAGE_TAGS, subtypes().map { it.attr("languageTag") })
    }

    @Test
    fun `every subtype has a keyboard mode, a locale and a string label`() {
        for (subtype in subtypes()) {
            val tag = subtype.attr("languageTag")
            assertEquals("keyboard", subtype.attr("imeSubtypeMode"), tag)
            assertEquals(tag.replace('-', '_'), subtype.attr("imeSubtypeLocale"), tag)
            assertTrue(subtype.attr("label").startsWith("@string/"), "$tag label must be a string resource")
        }
    }

    @Test
    fun `only Latin subtypes are ascii capable`() {
        val nonLatin = setOf("ar", "ur-PK", "hi-IN")
        for (subtype in subtypes()) {
            val tag = subtype.attr("languageTag")
            val ascii = subtype.attr("isAsciiCapable") == "true"
            if (tag in nonLatin) assertFalse(ascii, tag) else assertTrue(ascii, tag)
        }
    }

    @Test
    fun `label strings exist in strings_fork`() {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(mainDir, "res/values/strings_fork.xml"))
        val nodes = doc.getElementsByTagName("string")
        val strings = (0 until nodes.length).associate {
            val e = nodes.item(it) as Element
            e.getAttribute("name") to e.textContent
        }
        for (subtype in subtypes()) {
            val name = subtype.attr("label").removePrefix("@string/")
            assertTrue(!strings[name].isNullOrBlank(), "missing or empty string $name")
        }
    }
}
