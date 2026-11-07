/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.rc

import com.manjul.aikeyboard.core.config.ConfigCatalog
import com.manjul.aikeyboard.core.config.ConfigKeys
import com.manjul.aikeyboard.core.config.StringKey
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import org.w3c.dom.Element

/**
 * S4 Task 1: the Firebase Remote Config defaults XML (`setDefaultsAsync`) lists exactly the catalog keys, with the
 * catalog defaults. Ad unit ids are empty in main and Google's sample ids in debug, so a debug build never serves
 * real ads.
 */
class RemoteConfigDefaultsXmlTest {
    private val appDir: File = listOf(File("."), File("app"))
        .firstOrNull { File(it, "src/main/AndroidManifest.xml").isFile }
        ?: fail("Cannot locate the app module from ${File(".").absolutePath}")

    private fun entries(sourceSet: String): Map<String, String> {
        val file = File(appDir, "src/$sourceSet/res/xml/remote_config_defaults.xml")
        if (!file.isFile) fail("missing $file")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("entry")
        return (0 until nodes.length).associate { i ->
            val entry = nodes.item(i) as Element
            val key = entry.getElementsByTagName("key").item(0).textContent.trim()
            val value = entry.getElementsByTagName("value").item(0)?.textContent?.trim().orEmpty()
            key to value
        }
    }

    private val catalogNames = ConfigCatalog.all.map { it.name }.toSet()

    @Test
    fun `main and debug XML key sets equal the catalog`() {
        assertEquals(catalogNames, entries("main").keys)
        assertEquals(catalogNames, entries("debug").keys)
    }

    @Test
    fun `main XML values are the catalog defaults and ad units are empty`() {
        val main = entries("main")
        for (key in ConfigCatalog.all) {
            assertEquals(key.default.toString(), main.getValue(key.name), "default of ${key.name}")
        }
        for (unit in ConfigKeys.AD_UNITS) assertEquals("", main.getValue(unit), "$unit must be empty in main")
    }

    @Test
    fun `debug ad units are Google sample ids and other values match main`() {
        val debug = entries("debug")
        val main = entries("main")
        for (unit in ConfigKeys.AD_UNITS) {
            assertTrue(debug.getValue(unit).startsWith(SAMPLE_PREFIX), "$unit is not a Google sample id")
        }
        val others = ConfigCatalog.all.filterNot { it is StringKey && it.name in ConfigKeys.AD_UNITS }
        for (key in others) assertEquals(main.getValue(key.name), debug.getValue(key.name), key.name)
    }

    private companion object {
        const val SAMPLE_PREFIX = "ca-app-pub-3940256099942544/"
    }
}
