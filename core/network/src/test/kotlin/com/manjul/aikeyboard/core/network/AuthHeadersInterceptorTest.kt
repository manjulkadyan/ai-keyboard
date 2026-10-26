/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.network

import com.manjul.aikeyboard.core.ai.AiError
import com.manjul.aikeyboard.core.ai.AiException
import com.manjul.aikeyboard.core.ai.AuthHeaders
import com.manjul.aikeyboard.core.ai.TokenSource
import com.manjul.aikeyboard.core.ai.TokenState
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class AuthHeadersInterceptorTest {
    private lateinit var server: MockWebServer

    private class Tokens(var state: TokenState) : TokenSource {
        override suspend fun headers(): TokenState = state
        override fun prefetch() = Unit
    }

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun call(tokens: TokenSource) {
        val client = HttpClientFactory.create(tokens)
        client.newCall(Request.Builder().url(server.url("/v1/quota")).build()).execute().use { }
    }

    @Test
    fun `Ready headers are added`() {
        server.enqueue(MockResponse().setBody("{}"))
        call(
            Tokens(
                TokenState.Ready(
                    mapOf(AuthHeaders.APP_CHECK to "app-check-token", AuthHeaders.AUTHORIZATION to "Bearer id-token"),
                ),
            ),
        )
        val recorded = server.takeRequest()
        assertEquals("app-check-token", recorded.getHeader(AuthHeaders.APP_CHECK))
        assertEquals("Bearer id-token", recorded.getHeader(AuthHeaders.AUTHORIZATION))
    }

    @Test
    fun `dev auth header is added`() {
        server.enqueue(MockResponse().setBody("{}"))
        call(Tokens(TokenState.Ready(mapOf(AuthHeaders.DEV_UID to "dev-1"))))
        assertEquals("dev-1", server.takeRequest().getHeader(AuthHeaders.DEV_UID))
    }

    @Test
    fun `NotConfigured fails with AiError NotConfigured and sends no request`() {
        val e = assertFailsWith<AiException> { call(Tokens(TokenState.NotConfigured)) }
        assertEquals(AiError.NotConfigured, e.error)
        assertTrue(e is IOException, "OkHttp interceptors may only throw IOException")
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `failed tokens fail with Unavailable and send no request`() {
        val e = assertFailsWith<AiException> { call(Tokens(TokenState.Failed(retryable = false))) }
        assertEquals(AiError.Unavailable, e.error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `no install id or other identifying header is sent (decision 7)`() {
        server.enqueue(MockResponse().setBody("{}"))
        call(
            Tokens(
                TokenState.Ready(
                    mapOf(
                        AuthHeaders.APP_CHECK to "a",
                        AuthHeaders.AUTHORIZATION to "Bearer b",
                        "X-Install-Id" to "install-123",
                        "X-Device-Id" to "device-123",
                    ),
                ),
            ),
        )
        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("X-Install-Id"))
        assertNull(recorded.getHeader("X-Device-Id"))
        val allowed = setOf("host", "connection", "accept-encoding", "user-agent", "x-firebase-appcheck", "authorization")
        val names = recorded.headers.names().map { it.lowercase() }.toSet()
        assertEquals(emptySet(), names - allowed, "unexpected request headers")
        assertTrue(recorded.getHeader("User-Agent")!!.startsWith("okhttp/"), "User-Agent must be OkHttp's default")
    }
}
