/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.phrasebook

import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The bundled phrasebook DB (Task 1), copied to `no_backup/phrasebook-<version>.db` and opened read-only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhrasebookReaderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val asset = File(System.getProperty("content.phrasebookDb")!!)

    @Test
    fun twelveCategoriesInSortOrderWithThreePhrasesEach() {
        val dir = tmp.newFolder("no_backup")
        val reader = assertNotNull(PhrasebookReader.open(dir, version = 1) { asset.inputStream() })
        reader.use { r ->
            assertEquals(
                listOf(
                    "greetings", "basics", "directions", "transport", "hotel", "food", "shopping", "emergency",
                    "health", "numbers_time", "small_talk", "phone_internet",
                ),
                r.categories().map { it.key },
            )
            assertTrue(r.categories().none { it.virtual })
            for (category in r.categories()) assertEquals(3, r.phrases(category.key).size, category.key)
            assertEquals(listOf(1L, 2L, 3L), r.phrases("greetings").map { it.id })
            assertEquals("Hello, nice to meet you.", r.phrase(1)?.text)
            assertEquals("emergency", r.phrase(22)?.categoryKey)
            assertNull(r.phrase(999))
            assertTrue(r.phrases("nope").isEmpty())
        }
        assertEquals(listOf("phrasebook-1.db"), dir.list()!!.sorted())
    }

    @Test
    fun aSecondVersionReplacesTheFirstCopy() {
        val dir = tmp.newFolder("no_backup")
        PhrasebookReader.open(dir, version = 1) { asset.inputStream() }!!.close()
        // An unrelated file in no_backup stays.
        File(dir, "other.db").writeText("x")

        var copies = 0
        PhrasebookReader.open(dir, version = 1) { copies++; asset.inputStream() }!!.close()
        assertEquals(0, copies, "the same version is copied once")

        PhrasebookReader.open(dir, version = 2) { copies++; asset.inputStream() }!!.use { r ->
            assertEquals(12, r.categories().size)
        }
        assertEquals(1, copies)
        assertEquals(listOf("other.db", "phrasebook-2.db"), dir.list()!!.sorted())
    }

    @Test
    fun aBrokenAssetGivesNullAndLeavesNoCopy() {
        val dir = tmp.newFolder("no_backup")
        assertNull(PhrasebookReader.open(dir, version = 3) { ByteArrayInputStream("not a database".toByteArray()) })
        assertFalse(File(dir, "phrasebook-3.db").exists())
        assertNull(PhrasebookReader.open(dir, version = 4) { throw java.io.IOException("missing asset") })
        assertTrue(dir.list()!!.isEmpty())
    }
}
