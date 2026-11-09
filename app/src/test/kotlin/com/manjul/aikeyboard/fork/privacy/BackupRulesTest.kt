/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.privacy

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import org.w3c.dom.Element

/**
 * TRD §6.3: backups and device transfer carry only the upstream preferences (`jetpref_datastore`) and the installed
 * extensions (`files/ime`). Because every section uses `<include>`, the rest stays out: translation history and the
 * user dictionary (`databases/app_database`), the clipboard databases and files, `files/datastore` (consent and
 * Pro cache) and `files/themes`. Delete my data relies on this: a restore can not bring wiped data back.
 */
class BackupRulesTest {
    private val expected = setOf("include root:jetpref_datastore", "include file:ime")

    private fun xml(name: String): Element {
        val file = listOf(File("src/main"), File("app/src/main")).map { File(it, "res/xml/$name") }
            .firstOrNull { it.isFile } ?: fail("Cannot locate res/xml/$name from ${File(".").absolutePath}")
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
    }

    /** Every `<include>` and `<exclude>` under [section] as `tag domain:path`. */
    private fun rules(section: Element): Set<String> {
        val out = mutableSetOf<String>()
        val children = section.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue
            out += "${node.tagName} ${node.getAttribute("domain")}:${node.getAttribute("path")}"
        }
        return out
    }

    @Test
    fun `full backup content includes only the preferences and extensions`() {
        val root = xml("backup_rules.xml")
        assertEquals("full-backup-content", root.tagName)
        assertEquals(expected, rules(root))
    }

    @Test
    fun `data extraction rules include only the preferences and extensions for cloud and device transfer`() {
        val root = xml("data_extraction_rules.xml")
        assertEquals("data-extraction-rules", root.tagName)
        val sections = mutableMapOf<String, Element>()
        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue
            sections[node.tagName] = node
        }
        assertEquals(setOf("cloud-backup", "device-transfer"), sections.keys)
        for ((name, section) in sections) {
            assertEquals(expected, rules(section), name)
        }
    }
}
