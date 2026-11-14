/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.dictionary

import com.manjul.aikeyboard.core.content.TestContent
import java.io.File
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Task 1 fixture pack, read in place (plan F15). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DictionaryPackReaderTest {
    private val pack = File(TestContent.contentDir, "dict/en-fixture-1.db")
    private val reader = assertNotNull(DictionaryPackReader.open(pack))

    @After fun tearDown() = reader.close()

    @Test fun `a word is found whatever its case and surrounding spaces`() {
        val rows = reader.find("Run")
        assertEquals(rows, reader.find(" run "))
        assertEquals(listOf("noun", "verb", "verb", "verb"), rows.map { it.pos })
        assertEquals("run", rows.first().headword)
        assertEquals("An act of running.", rows.first().definition)
        assertEquals("I went for a run before work.", rows.first().example)
        assertEquals(listOf("jog"), rows.first().synonyms)
        assertEquals(listOf("sprint", "jog"), rows[1].synonyms)
    }

    @Test fun `a word without synonyms has an empty list`() {
        assertEquals(emptyList(), reader.find("Café").single().synonyms)
    }

    @Test fun `an unknown or blank word gives an empty list`() {
        assertTrue(reader.find("zebra").isEmpty())
        assertTrue(reader.find("   ").isEmpty())
    }

    @Test fun `meta gives the attribution and the version`() {
        val attribution = assertNotNull(reader.attribution)
        assertEquals("AI Keyboard fixture", attribution.source)
        assertEquals("AI Keyboard contributors", attribution.attribution)
        assertEquals("Apache-2.0", attribution.licenceName)
        assertEquals("https://www.apache.org/licenses/LICENSE-2.0", attribution.licenceUrl)
        assertEquals(1, reader.version)
    }

    @Test fun `a missing or broken file does not open`() {
        assertNull(DictionaryPackReader.open(File(pack.parentFile, "missing.db")))
        val junk = File.createTempFile("junk", ".db").apply { writeText("not a database") }
        try {
            assertNull(DictionaryPackReader.open(junk))
        } finally {
            junk.delete()
        }
    }
}
