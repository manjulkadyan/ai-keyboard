/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.tools.packbuild

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Guards the committed outputs (plan Task 1): run packbuild again and commit when one of these fails. */
class ShippedContentTest {
    @TempDir lateinit var tmp: File

    private val content get() = TestFiles.contentDir
    private val manifestBytes get() = File(content, "manifest.json").readBytes()

    @Test fun `the fixture manifest verifies with the fixture public key`() {
        val sig = File(content, "manifest.json.sig").readText()
        assertTrue(ManifestSigner.verify(manifestBytes, sig, ManifestSigner.publicKeyBase64(TestFiles.fixtureKeyPem)))
    }

    @Test fun `every file in the manifest exists with its size and sha256`() {
        val packs = Json.parseToJsonElement(manifestBytes.decodeToString()).jsonObject["packs"]!!.jsonArray
        // Plan F4: the debug manifest lists the dictionary pack and starter_plus. starter is bundled, never listed.
        assertEquals(listOf("en-fixture", "starter_plus"), packs.map { it.jsonObject["id"]!!.jsonPrimitive.content })
        for (f in packs.flatMap { it.jsonObject["files"]!!.jsonArray }.map { it.jsonObject }) {
            val path = f["path"]!!.jsonPrimitive.content
            val file = File(content, path)
            assertTrue(file.isFile, "missing $path")
            assertEquals(f["bytes"]!!.jsonPrimitive.long, file.length(), path)
            assertEquals(f["sha256"]!!.jsonPrimitive.content, sha256(file), path)
        }
    }

    @Test fun `the committed manifest matches the committed content`() {
        assertContentEquals(manifestBytes, ManifestWriter.write(content, packsTsv(), Main.FIXTURE_ISSUED_AT))
    }

    @Test fun `the committed dictionary pack matches its source`() {
        val out = File(tmp, "en.db")
        val jsonl = TestFiles.sourcesDir.resolve("dictionary/en-fixture.jsonl")
        DictPackBuilder.build(jsonl, out, "en", 1, DictPackBuilder.FIXTURE_LICENCE)
        assertContentEquals(out.readBytes(), File(content, "dict/en-fixture-1.db").readBytes())
    }

    @Test fun `the committed phrasebook matches its source`() {
        val out = File(tmp, "pb.db")
        PhrasebookBuilder.build(TestFiles.sourcesDir.resolve("phrasebook/phrases.tsv"), out)
        assertContentEquals(out.readBytes(), File(TestFiles.mainAssetsDir, "phrasebook/phrasebook-1.db").readBytes())
    }

    @Test fun `the bundled and the debug sticker packs hold 8 valid images each`() {
        val dirs = listOf(File(TestFiles.mainAssetsDir, "stickers/starter"), File(content, "stickers/starter_plus"))
        for (dir in dirs) {
            val files = dir.listFiles()!!.sortedBy { it.name }
            assertEquals((1..8).map { "%02d.png".format(it) }, files.map { it.name }, dir.path)
            for (f in files) {
                val img = ImageIO.read(f)
                assertEquals(512 to 512, img.width to img.height, f.path)
                assertTrue(f.length() <= 60 * 1024, "${f.path} is ${f.length()} bytes")
            }
        }
    }

    @Test fun `LICENSES md records every content set`() {
        val licences = File(TestFiles.mainAssetsDir, "LICENSES.md").readText()
        for (path in listOf("phrasebook/phrasebook-1.db", "stickers/starter/", "content/v1/")) {
            assertTrue(path in licences, "LICENSES.md does not name $path")
        }
        assertTrue(":tools:packbuild" in licences)
    }

    private fun packsTsv() = TestFiles.sourcesDir.resolve("stickers/packs.tsv")

    private fun sha256(f: File): String =
        MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
}
