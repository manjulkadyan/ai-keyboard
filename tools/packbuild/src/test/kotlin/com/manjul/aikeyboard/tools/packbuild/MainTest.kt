/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.tools.packbuild

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** The `dict` command never labels a pack with a licence that nobody gave it (review 1, Important 1; R-08). */
class MainTest {
    @TempDir lateinit var root: File

    private val input get() = TestFiles.sourcesDir.resolve("dictionary/en-fixture.jsonl")
    private val out get() = File(root, "dict/en-real-2.db")

    private val licenceFlags = listOf(
        "--source", "Wiktextract (kaikki.org)",
        "--licence-name", "CC-BY-SA-4.0",
        "--licence-url", "https://creativecommons.org/licenses/by-sa/4.0/",
        "--attribution", "Wiktionary contributors",
    )

    private fun dict(vararg extra: String) =
        Main.run(listOf("dict", input.path, out.path, "--lang", "en", "--version", "2") + extra)

    @Test fun `dict without licence flags fails and writes nothing`() {
        val e = assertFailsWith<IllegalArgumentException> { dict() }
        assertContains(e.message!!, "--licence-name")
        assertFalse(out.exists())
    }

    @ParameterizedTest
    @ValueSource(strings = ["source", "licence-name", "licence-url", "attribution"])
    fun `dict with one licence flag missing fails`(missing: String) {
        val flags = licenceFlags.chunked(2).filter { it[0] != "--$missing" }.flatten()
        val e = assertFailsWith<IllegalArgumentException> { dict(*flags.toTypedArray()) }
        assertContains(e.message!!, "--$missing")
        assertFalse(out.exists())
    }

    @Test fun `--fixture together with licence flags fails`() {
        assertFailsWith<IllegalArgumentException> { dict("--fixture", *licenceFlags.toTypedArray()) }
        assertFalse(out.exists())
    }

    @Test fun `the given licence reaches meta and the manifest entry`() {
        dict(*licenceFlags.toTypedArray())
        val meta = TestFiles.rows(out, "SELECT key, value FROM meta").associate { it[0]!! to it[1]!! }
        assertEquals("Wiktextract (kaikki.org)", meta["source"])
        assertEquals("CC-BY-SA-4.0", meta["licence_name"])
        assertEquals("https://creativecommons.org/licenses/by-sa/4.0/", meta["licence_url"])
        assertEquals("Wiktionary contributors", meta["attribution"])

        val manifest = File(root, "manifest.json")
        val packs = TestFiles.sourcesDir.resolve("stickers/packs.tsv")
        Main.run(listOf("manifest", root.path, manifest.path, "--packs", packs.path, "--issued-at", "1"))
        val pack = Json.parseToJsonElement(manifest.readText()).jsonObject["packs"]!!.jsonArray
            .map { it.jsonObject }.single { it["id"]!!.jsonPrimitive.content == "en-real" }
        val licence = pack["licence"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content }
        assertEquals(
            mapOf(
                "name" to "CC-BY-SA-4.0",
                "url" to "https://creativecommons.org/licenses/by-sa/4.0/",
                "attribution" to "Wiktionary contributors",
            ),
            licence,
        )
    }

    @Test fun `--fixture stamps the team licence`() {
        dict("--fixture")
        val meta = TestFiles.rows(out, "SELECT key, value FROM meta").associate { it[0]!! to it[1]!! }
        assertEquals(TEAM_LICENCE.metaRows().toMap(), meta.filterKeys { it in TEAM_LICENCE.metaRows().toMap() })
    }
}
