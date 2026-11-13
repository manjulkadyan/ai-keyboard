/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.manifest

import com.manjul.aikeyboard.core.content.TestContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ManifestVerifierTest {
    private val verifier = ManifestVerifier(TestContent.publicKeyBase64)
    private val json get() = TestContent.manifestBytes.decodeToString()

    /** [text] signed with the fixture key, so only the content check can reject it. */
    private fun resigned(text: String): ContentManifest? {
        val bytes = text.toByteArray()
        return verifier.verify(bytes, TestContent.sign(bytes))
    }

    @Test fun `the shipped fixture manifest verifies and parses`() {
        val manifest = assertNotNull(verifier.verify(TestContent.manifestBytes, TestContent.manifestSig))
        assertEquals(1, manifest.format)
        assertEquals(1_794_614_400_000, manifest.issuedAt)
        assertEquals(listOf("en-fixture", "starter_plus"), manifest.packs.map { it.id })

        val dict = manifest.packs[0]
        assertEquals(PackKindSpec.DICT, dict.kind)
        assertEquals(1, dict.version)
        assertEquals(false, dict.premium)
        assertEquals("English dictionary", dict.title)
        val file = dict.files.single()
        assertEquals("dict/en-fixture-1.db", file.path)
        assertEquals("379f6996c5e6bc9f33d93e47672f91e5e88d0805ba4ebd6e271deef045ed47f6", file.sha256)
        assertEquals(28_672L, file.bytes)
        assertEquals("application/vnd.sqlite3", file.mime)
        val licence =
            PackLicence("Apache-2.0", "https://www.apache.org/licenses/LICENSE-2.0", "AI Keyboard contributors")
        assertEquals(licence, dict.licence)

        val stickers = manifest.packs[1]
        assertEquals(PackKindSpec.STICKERS, stickers.kind)
        assertEquals(true, stickers.premium)
        assertEquals(8, stickers.files.size)
    }

    @Test fun `one changed byte gives null`() {
        val bytes = TestContent.manifestBytes
        for (i in listOf(0, bytes.size / 2, bytes.size - 1)) {
            val changed = bytes.copyOf().also { it[i] = (it[i] + 1).toByte() }
            assertNull(verifier.verify(changed, TestContent.manifestSig), "byte $i")
        }
    }

    @Test fun `an empty or garbled signature gives null`() {
        assertNull(verifier.verify(TestContent.manifestBytes, ""))
        assertNull(verifier.verify(TestContent.manifestBytes, "not base64!"))
        assertNull(verifier.verify(TestContent.manifestBytes, "AAAA"))
    }

    @Test fun `another key gives null`() {
        val other = ManifestVerifier(TestContent.otherPublicKeyBase64())
        assertNull(other.verify(TestContent.manifestBytes, TestContent.manifestSig))
    }

    @Test fun `an empty or broken public key gives null and never throws`() {
        assertNull(ManifestVerifier("").verify(TestContent.manifestBytes, TestContent.manifestSig))
        assertNull(ManifestVerifier("bm90IGEga2V5").verify(TestContent.manifestBytes, TestContent.manifestSig))
    }

    @Test fun `a correctly signed copy parses, so the checks below test the content only`() {
        assertNotNull(resigned(json))
    }

    @Test fun `format 2 gives null`() {
        assertNull(resigned(json.replace("\"format\":1", "\"format\":2")))
    }

    @Test fun `a missing sha256 gives null`() {
        val missing = json.replaceFirst(Regex(""""sha256":"[0-9a-f]{64}","""), "")
        check(missing != json)
        assertNull(resigned(missing))
    }

    @Test fun `a malformed sha256 or size gives null`() {
        assertNull(resigned(json.replaceFirst("379f6996", "379F6996")))
        assertNull(resigned(json.replaceFirst("\"bytes\":28672", "\"bytes\":-1")))
    }

    @Test fun `a path or id that leaves the pack folder gives null`() {
        assertNull(resigned(json.replace("dict/en-fixture-1.db", "../en-fixture-1.db")))
        assertNull(resigned(json.replace("dict/en-fixture-1.db", "/dict/en-fixture-1.db")))
        assertNull(resigned(json.replace("dict/en-fixture-1.db", "dict/en-fixture-1.db?x=1")))
        assertNull(resigned(json.replace("\"id\":\"en-fixture\"", "\"id\":\"../en\"")))
    }

    @Test fun `two files with the same name in one pack give null`() {
        assertNull(resigned(json.replace("stickers/starter_plus/02.png", "stickers/other/01.png")))
    }

    @Test fun `a duplicate pack id gives null`() {
        assertNull(resigned(json.replace("\"id\":\"starter_plus\"", "\"id\":\"en-fixture\"")))
    }

    @Test fun `a pack of an unknown kind is skipped, the others stay`() {
        val manifest = assertNotNull(resigned(json.replace("\"kind\":\"stickers\"", "\"kind\":\"fonts\"")))
        assertEquals(listOf("en-fixture"), manifest.packs.map { it.id })
    }

    @Test fun `not json gives null`() {
        assertNull(resigned("[]"))
        assertNull(resigned("{"))
    }
}
