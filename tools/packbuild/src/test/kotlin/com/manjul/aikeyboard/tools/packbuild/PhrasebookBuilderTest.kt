/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.tools.packbuild

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhrasebookBuilderTest {
    @TempDir lateinit var tmp: File

    private val source get() = TestFiles.sourcesDir.resolve("phrasebook/phrases.tsv")

    @Test fun `the fixture gives 12 categories with 36 English phrases`() {
        val db = File(tmp, "pb.db").also { PhrasebookBuilder.build(source, it) }
        val cats = TestFiles.rows(db, "SELECT id, key, sort FROM phrase_category ORDER BY sort")
        assertEquals(12, cats.size)
        assertEquals(12, cats.map { it[1] }.distinct().size)
        assertEquals(listOf(listOf("36")), TestFiles.rows(db, "SELECT COUNT(*) FROM phrase WHERE lang = 'en'"))
        val perCategory = TestFiles.rows(db, "SELECT category_id, COUNT(*) FROM phrase GROUP BY category_id")
        assertTrue(perCategory.all { it[1] == "3" }, "each category holds 3 phrases: $perCategory")
        val withHint = TestFiles.rows(db, "SELECT COUNT(*) FROM phrase WHERE audio_hint IS NOT NULL")
        assertEquals(listOf(listOf("0")), withHint)
        val meta = TestFiles.rows(db, "SELECT key, value FROM meta").associate { it[0] to it[1] }
        assertEquals("1", meta["format"])
        assertEquals("1", meta["version"])
        assertEquals("Apache-2.0", meta["licence_name"])
    }

    @Test fun `a duplicate phrase_id fails the build`() {
        val tsv = File(tmp, "dup.tsv").apply {
            writeText(
                "category_key\tsort\tphrase_id\tlang\ttext\n" +
                    "greetings\t10\t1\ten\tHello.\n" +
                    "basics\t20\t1\ten\tYes.\n",
            )
        }
        val e = assertThrows<IllegalArgumentException> { PhrasebookBuilder.build(tsv, File(tmp, "dup.db")) }
        assertTrue("phrase_id 1" in e.message.orEmpty(), e.message)
    }

    @Test fun `two builds of the same input are byte-identical`() {
        val a = File(tmp, "a.db").also { PhrasebookBuilder.build(source, it) }
        val b = File(tmp, "b.db").also { PhrasebookBuilder.build(source, it) }
        assertContentEquals(a.readBytes(), b.readBytes())
    }
}
