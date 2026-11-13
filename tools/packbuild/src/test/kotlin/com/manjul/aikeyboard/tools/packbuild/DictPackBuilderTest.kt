/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.tools.packbuild

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DictPackBuilderTest {
    @TempDir lateinit var tmp: File

    private fun jsonl(vararg lines: String): File = File(tmp, "in.jsonl").apply { writeText(lines.joinToString("\n")) }

    private fun build(input: File, name: String = "out.db"): File =
        File(tmp, name).also { DictPackBuilder.build(input, it, lang = "en", version = 3) }

    @Test fun `two parts of speech of run give two rows with the right pos`() {
        val db = build(
            jsonl(
                """{"word":"run","pos":"verb","senses":[{"glosses":["To move fast on foot."],""" +
                    """"examples":[{"text":"She runs."}]}],"synonyms":[{"word":"sprint"}]}""",
                """{"word":"run","pos":"noun","senses":[{"glosses":["An act of running."]}]}""",
            ),
        )
        val rows = TestFiles.rows(
            db,
            "SELECT headword, headword_norm, pos, definition, example, synonyms FROM entry ORDER BY id",
        )
        assertEquals(
            listOf(
                listOf("run", "run", "noun", "An act of running.", null, null),
                listOf("run", "run", "verb", "To move fast on foot.", "She runs.", """["sprint"]"""),
            ),
            rows,
        )
    }

    @Test fun `an entry without a gloss is dropped`() {
        val db = build(
            jsonl(
                """{"word":"colour","pos":"noun","senses":[{"tags":["no-gloss"]}]}""",
                """{"word":"blank","pos":"noun","senses":[{"glosses":["  "]}]}""",
                """{"word":"none","pos":"noun"}""",
                """{"word":"kept","pos":"verb","senses":[{"glosses":["Past of keep."]}]}""",
            ),
        )
        assertEquals(listOf(listOf("kept")), TestFiles.rows(db, "SELECT headword FROM entry"))
    }

    @Test fun `keeps the first three senses per headword and part of speech`() {
        val senses = (1..5).joinToString(",") { """{"glosses":["Sense $it."]}""" }
        val db = build(
            jsonl(
                """{"word":"Set","pos":"verb","senses":[$senses]}""",
                """{"word":"Set","pos":"verb","senses":[{"glosses":["Sense 6."]}]}""",
            ),
        )
        assertEquals(
            (1..3).map { listOf("Set", "set", "Sense $it.") },
            TestFiles.rows(db, "SELECT headword, headword_norm, definition FROM entry ORDER BY id"),
        )
    }

    @Test fun `meta holds the format, the version and the licence`() {
        val db = build(jsonl("""{"word":"run","pos":"verb","senses":[{"glosses":["Go."]}]}"""))
        val meta = TestFiles.rows(db, "SELECT key, value FROM meta").associate { it[0] to it[1] }
        assertEquals("1", meta["format"])
        assertEquals("en", meta["lang"])
        assertEquals("3", meta["version"])
        assertEquals(DictPackBuilder.FIXTURE_LICENCE.source, meta["source"])
        assertEquals("Apache-2.0", meta["licence_name"])
        assertEquals("https://www.apache.org/licenses/LICENSE-2.0", meta["licence_url"])
        assertEquals("AI Keyboard contributors", meta["attribution"])
    }

    @Test fun `the index on headword_norm exists`() {
        val db = build(jsonl("""{"word":"run","pos":"verb","senses":[{"glosses":["Go."]}]}"""))
        assertEquals(
            listOf(listOf("idx_entry_norm")),
            TestFiles.rows(db, "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'entry'"),
        )
    }

    @Test fun `two builds of the same input are byte-identical`() {
        val input = TestFiles.sourcesDir.resolve("dictionary/en-fixture.jsonl")
        val a = build(input, "a.db")
        val b = build(input, "b.db")
        assertContentEquals(a.readBytes(), b.readBytes())
        assertNull(tmp.listFiles()!!.firstOrNull { it.name.endsWith("-journal") }, "a journal file was left behind")
    }
}
