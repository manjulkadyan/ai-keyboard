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

        assertContentEquals("one".toByteArray(), src.fetch("manifest.json"))
        assertContentEquals("two".toByteArray(), src.fetch("stickers/starter_plus/01.png"))

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
        source().fetch("manifest.json")
        val names = server.takeRequest().headers.names().map { it.lowercase() }.toSet()
        // OkHttp's transport headers plus our User-Agent; nothing that could identify the user or install.
        assertTrue(names.all { it in setOf("host", "connection", "accept-encoding", "user-agent") }, "$names")
    }

    @Test fun `an HTTP error throws ContentFetchException without the URL`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val e = assertThrows<ContentFetchException> { source().fetch("manifest.json") }
        assertEquals("HTTP 404", e.message)
    }

    @Test fun `a network failure throws ContentFetchException`() = runTest {
        val url = server.url("/v1/").toString()
        server.shutdown()
        assertThrows<ContentFetchException> { HttpContentSource(url).fetch("manifest.json") }
    }

    @Test fun `a path with a query, a fragment or a parent segment is refused before any request`() = runTest {
        val src = source()
        for (bad in listOf("manifest.json?uid=1", "manifest.json#x", "../secret", "/abs", "", "a//b", "a\\b")) {
            assertThrows<ContentFetchException>(bad) { src.fetch(bad) }
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun `a base URL with a query string is rejected`() {
        assertThrows<IllegalArgumentException> { HttpContentSource("https://cdn.example.com/v1/?k=1") }
    }
}
