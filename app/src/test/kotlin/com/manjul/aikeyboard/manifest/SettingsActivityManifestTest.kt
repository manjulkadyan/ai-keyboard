/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.manifest

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * TRD §2.1 "Settings activity": the `android:settingsActivity` of the IME (and the spell checker) must
 * point at an activity that is declared in the manifest, exported and enabled. Robolectric is not
 * available, so this parses the source XML files directly.
 */
class SettingsActivityManifestTest {
    private val androidNs = "http://schemas.android.com/apk/res/android"
    private val appNamespace = "com.manjul.aikeyboard"

    // Disabled at runtime by Context.hideAppIcon(), so it must never be the settings target.
    private val launcherAlias = "$appNamespace.SettingsLauncherAlias"

    private val mainDir: File = listOf(File("src/main"), File("app/src/main"))
        .firstOrNull { File(it, "AndroidManifest.xml").isFile }
        ?: fail("Cannot locate app/src/main from working dir ${File(".").absolutePath}")

    private fun parse(file: File): Document {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder().parse(file)
    }

    private fun qualify(name: String): String = if (name.startsWith(".")) appNamespace + name else name

    private fun settingsActivityOf(xmlFile: String): String {
        val root = parse(File(mainDir, "res/xml/$xmlFile")).documentElement
        val value = root.getAttributeNS(androidNs, "settingsActivity")
        assertTrue(value.isNotBlank(), "$xmlFile has no android:settingsActivity")
        return qualify(value)
    }

    private fun declaredActivities(): Map<String, Element> {
        val doc = parse(File(mainDir, "AndroidManifest.xml"))
        val result = mutableMapOf<String, Element>()
        for (tag in listOf("activity", "activity-alias")) {
            val nodes = doc.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val element = nodes.item(i) as Element
                result[qualify(element.getAttributeNS(androidNs, "name"))] = element
            }
        }
        return result
    }

    private fun assertResolvable(xmlFile: String) {
        val target = settingsActivityOf(xmlFile)
        assertNotEquals(launcherAlias, target, "$xmlFile must not use the launcher alias that hideAppIcon() disables")
        val element = assertNotNull(declaredActivities()[target], "$xmlFile settingsActivity $target is not declared in AndroidManifest.xml")
        assertEquals("true", element.getAttributeNS(androidNs, "exported"), "$target must be android:exported=\"true\"")
        assertNotEquals("false", element.getAttributeNS(androidNs, "enabled"), "$target must not be disabled")
        assertTrue(
            File(mainDir, "kotlin/" + target.replace('.', '/') + ".kt").isFile,
            "$target has no source file",
        )
    }

    @Test
    fun `IME method xml settingsActivity resolves to an exported activity`() {
        assertResolvable("method.xml")
    }

    @Test
    fun `spell checker settingsActivity resolves to an exported activity`() {
        assertResolvable("spellchecker.xml")
    }
}
