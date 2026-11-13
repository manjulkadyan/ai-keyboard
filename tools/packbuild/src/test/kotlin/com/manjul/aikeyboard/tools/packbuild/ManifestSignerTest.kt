/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.tools.packbuild

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManifestSignerTest {
    @TempDir lateinit var root: File

    private val packs get() = TestFiles.sourcesDir.resolve("stickers/packs.tsv")

    @BeforeEach fun content() {
        val jsonl = TestFiles.sourcesDir.resolve("dictionary/en-fixture.jsonl")
        DictPackBuilder.build(jsonl, File(root, "dict/en-fixture-1.db"), "en", 1, DictPackBuilder.FIXTURE_LICENCE)
        StickerArt.buildPacks(packs, File(root, "stickers"), only = "starter_plus")
    }

    private fun manifest(): ByteArray = ManifestWriter.write(root, packs, issuedAt = 1_794_614_400_000)

    @Test fun `a signed manifest verifies with the public key`() {
        val bytes = manifest()
        val sig = ManifestSigner.sign(bytes, TestFiles.fixtureKeyPem)
        assertTrue(ManifestSigner.verify(bytes, sig, ManifestSigner.publicKeyBase64(TestFiles.fixtureKeyPem)))
    }

    @Test fun `one changed byte fails`() {
        val bytes = manifest()
        val sig = ManifestSigner.sign(bytes, TestFiles.fixtureKeyPem)
        val key = ManifestSigner.publicKeyBase64(TestFiles.fixtureKeyPem)
        for (i in listOf(0, bytes.size / 2, bytes.size - 1)) {
            val changed = bytes.copyOf().also { it[i] = (it[i] + 1).toByte() }
            assertFalse(ManifestSigner.verify(changed, sig, key), "byte $i changed")
        }
    }

    @Test fun `every listed sha256 matches its file`() {
        val json = Json.parseToJsonElement(manifest().decodeToString()).jsonObject
        val files = json["packs"]!!.jsonArray.flatMap { it.jsonObject["files"]!!.jsonArray }.map { it.jsonObject }
        assertEquals(9, files.size) // the dictionary pack and the 8 starter_plus stickers
        for (f in files) {
            val file = File(root, f.string("path"))
            val sha = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            assertEquals(sha.joinToString("") { "%02x".format(it) }, f.string("sha256"), f.string("path"))
            assertEquals(file.length(), f["bytes"]!!.jsonPrimitive.long)
        }
    }

    @Test fun `the manifest describes both packs`() {
        val json = Json.parseToJsonElement(manifest().decodeToString()).jsonObject
        assertEquals(1, json["format"]!!.jsonPrimitive.int)
        assertEquals(1_794_614_400_000, json["issued_at"]!!.jsonPrimitive.long)
        val byId = json["packs"]!!.jsonArray.map { it.jsonObject }.associateBy { it.string("id") }
        assertEquals(setOf("en-fixture", "starter_plus"), byId.keys)
        val dict = byId.getValue("en-fixture")
        assertEquals("dict", dict.string("kind"))
        assertEquals("false", dict["premium"]!!.jsonPrimitive.content)
        assertEquals("application/vnd.sqlite3", dict.files().single().string("mime"))
        assertEquals("dict/en-fixture-1.db", dict.files().single().string("path"))
        assertEquals("Apache-2.0", dict["licence"]!!.jsonObject.string("name"))
        val plus = byId.getValue("starter_plus")
        assertEquals("stickers", plus.string("kind"))
        assertEquals("true", plus["premium"]!!.jsonPrimitive.content)
        assertEquals("Starter Plus", plus.string("title"))
        assertEquals("stickers/starter_plus/01.png", plus.files().first().string("path"))
        assertEquals("AI Keyboard contributors", plus["licence"]!!.jsonObject.string("attribution"))
    }

    @Test fun `the same content gives the same manifest bytes`() {
        assertContentEquals(manifest(), manifest())
    }

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
    private fun JsonObject.files(): List<JsonObject> = getValue("files").jsonArray.map { it.jsonObject }
}
