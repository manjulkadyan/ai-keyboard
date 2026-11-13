/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.source

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class ContentSourcesTest {
    private val assets = object : ContentSource {
        override suspend fun fetchTo(path: String, sink: OutputStream, maxBytes: Long): Long = 0
    }

    @Test fun `a base URL always wins`() {
        assertIs<HttpContentSource>(ContentSources.select("https://cdn.example.com/v1/", debug = true) { assets })
        assertIs<HttpContentSource>(ContentSources.select("https://cdn.example.com/v1/", debug = false) { assets })
    }

    @Test fun `an empty base URL reads the app assets in debug only`() {
        assertSame(assets, ContentSources.select("", debug = true) { assets })
        assertSame(UnavailableContentSource, ContentSources.select("", debug = false) { error("no assets") })
    }

    @Test fun `the unavailable source never answers`() = runTest {
        assertThrows<ContentFetchException> { UnavailableContentSource.fetchBytes("manifest.json", 10) }
    }

    @Test fun `copyBounded copies up to the limit and stops one byte past it`() {
        val out = ByteArrayOutputStream()
        assertEquals(5L, copyBounded("12345".byteInputStream(), out, maxBytes = 5))
        assertEquals("12345", out.toString())
        val input = ByteArray(100_000).inputStream()
        assertThrows<ContentTooLargeException> { copyBounded(input, ByteArrayOutputStream(), maxBytes = 10) }
        assertEquals(100_000 - 11, input.available()) // it asked for 11 bytes, no more
    }
}
