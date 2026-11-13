/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.manifest

import com.manjul.aikeyboard.core.content.FakeContentSource
import com.manjul.aikeyboard.core.content.TestContent
import com.manjul.aikeyboard.core.content.source.UnavailableContentSource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManifestCacheTest {
    @TempDir lateinit var dir: File

    private val verifier = ManifestVerifier(TestContent.publicKeyBase64)
    private var now = 1_800_000_000_000L
    private val source = FakeContentSource(
        mapOf(
            ManifestCache.MANIFEST_PATH to TestContent.manifestBytes,
            ManifestCache.SIGNATURE_PATH to TestContent.manifestSig.toByteArray(),
        ),
    )

    private fun cache(src: com.manjul.aikeyboard.core.content.source.ContentSource = source) =
        ManifestCache(dir, src, verifier, clock = { now })

    /** The fixture manifest with another `issued_at`, signed with the fixture key. */
    private fun publish(issuedAt: Long, packTitle: String = "English dictionary") {
        val bytes = TestContent.manifestBytes.decodeToString()
            .replace("\"issued_at\":1794614400000", "\"issued_at\":$issuedAt")
            .replace("\"title\":\"English dictionary\"", "\"title\":\"$packTitle\"")
            .toByteArray()
        source.files = mapOf(
            ManifestCache.MANIFEST_PATH to bytes,
            ManifestCache.SIGNATURE_PATH to TestContent.sign(bytes).toByteArray(),
        )
    }

    @Test fun `the first call fetches the manifest and its signature`() = runTest {
        val manifest = assertNotNull(cache().current())
        assertEquals(listOf("en-fixture", "starter_plus"), manifest.packs.map { it.id })
        assertEquals(listOf(ManifestCache.MANIFEST_PATH, ManifestCache.SIGNATURE_PATH), source.requests)
    }

    @Test fun `a second call within 24 h makes no request, also from a new instance`() = runTest {
        cache().current()
        source.requests.clear()
        now += ManifestCache.MAX_AGE_MS - 1
        assertNotNull(cache().current())
        val same = cache()
        assertNotNull(same.current())
        assertNotNull(same.current())
        assertEquals(emptyList(), source.requests)
    }

    @Test fun `after 24 h a newer good manifest replaces the old one`() = runTest {
        val c = cache()
        c.current()
        publish(issuedAt = 1_794_614_400_001, packTitle = "Newer")
        now += ManifestCache.MAX_AGE_MS
        assertEquals("Newer", c.current()!!.packs[0].title)
        assertEquals("Newer", cache().current()!!.packs[0].title) // persisted
    }

    @Test fun `after 24 h a bad new manifest keeps the old one`() = runTest {
        val c = cache()
        val old = assertNotNull(c.current())
        source.files = source.files + (ManifestCache.SIGNATURE_PATH to "AAAA".toByteArray())
        now += ManifestCache.MAX_AGE_MS
        source.requests.clear()
        assertEquals(old, c.current())
        assertEquals(2, source.requests.size) // it did try
        assertEquals(old, cache().current()) // the stored copy is still the old one
        assertEquals(2, source.requests.size) // and the rejected answer started the next 24 h wait
    }

    @Test fun `after 24 h an older signed manifest does not roll the cache back`() = runTest {
        val c = cache()
        val old = assertNotNull(c.current())
        publish(issuedAt = 1_794_614_399_999, packTitle = "Older")
        now += ManifestCache.MAX_AGE_MS
        assertEquals(old, c.current())
        source.requests.clear()
        assertEquals(old, c.current())
        assertEquals(emptyList(), source.requests)
    }

    @Test fun `a rejected first answer with no stored copy also waits 24 h`() = runTest {
        source.files = source.files + (ManifestCache.SIGNATURE_PATH to "AAAA".toByteArray())
        val c = cache()
        assertNull(c.current())
        assertEquals(2, source.requests.size)
        now += ManifestCache.MAX_AGE_MS - 1
        assertNull(c.current())
        assertNull(cache().current()) // a new instance reads the same stamp
        assertEquals(2, source.requests.size)
        now += 1
        source.files = mapOf(
            ManifestCache.MANIFEST_PATH to TestContent.manifestBytes,
            ManifestCache.SIGNATURE_PATH to TestContent.manifestSig.toByteArray(),
        )
        assertNotNull(c.current()) // after 24 h it asks again
        assertEquals(4, source.requests.size)
    }

    @Test fun `an oversized manifest is rejected without being read in full`() = runTest {
        source.files = source.files + (ManifestCache.MANIFEST_PATH to ByteArray(4 * 1024 * 1024))
        assertNull(cache().current())
        assertTrue(source.read <= ManifestCache.MAX_MANIFEST_BYTES + 1, "read ${source.read}")
    }

    @Test fun `clear forgets the in-memory copy and the stored files`() = runTest {
        val c = cache()
        assertNotNull(c.current())
        c.clear()
        assertFalse(dir.exists())
        source.offline = true
        assertNull(c.current())
    }

    @Test fun `with no network and no cache it returns null`() = runTest {
        source.offline = true
        assertNull(cache().current())
    }

    @Test fun `with no network after 24 h it keeps the last good copy, and retries on the next call`() = runTest {
        val c = cache()
        val old = assertNotNull(c.current())
        now += ManifestCache.MAX_AGE_MS
        source.offline = true
        source.requests.clear()
        assertEquals(old, c.current())
        assertEquals(old, c.current())
        assertEquals(2, source.requests.size) // a failed fetch does not start the 24 h wait
    }

    @Test fun `a cache file changed on disk is not trusted`() = runTest {
        cache().current()
        val file = dir.listFiles()!!.single { it.name == "manifest.json" }
        file.writeText(file.readText().replace("English dictionary", "Tampered"))
        source.offline = true
        assertNull(cache().current())
    }

    @Test fun `an empty base URL in beta or release gives null and sends nothing`() = runTest {
        assertNull(cache(UnavailableContentSource).current())
    }
}
