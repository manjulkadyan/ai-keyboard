/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.source

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpContentSourceTest {
    private val server = MockWebServer()

    @BeforeEach fun start() = server.start()

    @AfterEach fun stop() = server.shutdown()

    private fun source() = HttpContentSource(server.url("/cdn/v1/").toString())

    @Test fun `a fetch sends no identifier, no cookie and no query string`() = runTest {
        // A Set-Cookie on the first answer must not come back on the second request.
        server.enqueue(MockResponse().setBody("one").addHeader("Set-Cookie", "sid=abc; Path=/"))
        server.enqueue(MockResponse().setBody("two"))
        val src = source()

        assertContentEquals("one".toByteArray(), src.fetchBytes("manifest.json", 100))
        assertContentEquals("two".toByteArray(), src.fetchBytes("stickers/starter_plus/01.png", 100))

        for (expected in listOf("/cdn/v1/manifest.json", "/cdn/v1/stickers/starter_plus/01.png")) {
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals(expected, request.path)
            assertNull(request.requestUrl!!.query)
            assertNull(request.getHeader("Authorization"))
            assertNull(request.getHeader("X-Firebase-AppCheck"))
            assertNull(request.getHeader("X-Dev-Uid"))
            assertNull(request.getHeader("Cookie"))
            assertEquals("AIKeyboard-Content/1", request.getHeader("User-Agent"))
        }
    }

    @Test fun `only the fixed headers are sent`() = runTest {
        server.enqueue(MockResponse().setBody("x"))
        source().fetchBytes("manifest.json", 100)
        val names = server.takeRequest().headers.names().map { it.lowercase() }.toSet()
        // OkHttp's transport headers plus our User-Agent; nothing that could identify the user or install.
        assertTrue(names.all { it in setOf("host", "connection", "accept-encoding", "user-agent") }, "$names")
    }

    @Test fun `an HTTP error throws ContentFetchException without the URL`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val e = assertThrows<ContentFetchException> { source().fetchBytes("manifest.json", 100) }
        assertEquals("HTTP 404", e.message)
    }

    @Test fun `a network failure throws ContentFetchException`() = runTest {
        val url = server.url("/v1/").toString()
        server.shutdown()
        assertThrows<ContentFetchException> { HttpContentSource(url).fetchBytes("manifest.json", 100) }
    }

    @Test fun `a path with a query, a fragment or a parent segment is refused before any request`() = runTest {
        val src = source()
        for (bad in listOf("manifest.json?uid=1", "manifest.json#x", "../secret", "/abs", "", "a//b", "a\\b")) {
            assertThrows<ContentFetchException>(bad) { src.fetchBytes(bad, 100) }
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun `a base URL with a query string is rejected`() {
        assertThrows<IllegalArgumentException> { HttpContentSource("https://cdn.example.com/v1/?k=1") }
    }

    @Test fun `a body larger than the limit fails without being read in full`() = runTest {
        // Chunked, so there is no Content-Length: the bounded copy itself must stop.
        server.enqueue(MockResponse().setChunkedBody("x".repeat(4 * 1024 * 1024), 64 * 1024))
        val sink = CountingSink()
        assertThrows<ContentTooLargeException> { source().fetchTo("dict/en.db", sink, maxBytes = 1000) }
        assertTrue(sink.count <= 1000, "${sink.count}") // the bytes past the limit are never written
    }

    @Test fun `a declared Content-Length over the limit fails before the body is read`() = runTest {
        server.enqueue(MockResponse().setBody("x".repeat(5000)))
        val sink = CountingSink()
        assertThrows<ContentTooLargeException> { source().fetchTo("dict/en.db", sink, maxBytes = 1000) }
        assertEquals(0L, sink.count)
    }

    @Test fun `a body within the limit streams into the sink`() = runTest {
        server.enqueue(MockResponse().setBody("x".repeat(1000)))
        val sink = CountingSink()
        assertEquals(1000L, source().fetchTo("dict/en.db", sink, maxBytes = 1000))
        assertEquals(1000L, sink.count)
    }

    private class CountingSink : OutputStream() {
        var count = 0L

        override fun write(b: Int) {
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            count += len
        }
    }
}
