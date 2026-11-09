/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ai.kb

import com.manjul.aikeyboard.core.ai.AiError
import com.manjul.aikeyboard.core.ai.AuthHeaders
import com.manjul.aikeyboard.core.ai.FakeTokenSource
import com.manjul.aikeyboard.core.ai.TokenState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class QuotaClientTest {
    private lateinit var server: MockWebServer
    private val tokens = FakeTokenSource()
    private val http = OkHttpClient()

    private fun client(baseUrl: String = server.url("/").toString()) = QuotaClient(http, baseUrl, tokens)

    private fun json(code: Int, body: String) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `200 parses left, reset time and tier`() = runBlocking {
        server.enqueue(json(200, """{"ai_left_today":7,"resets_at":1800000000000,"tier":"FREE"}"""))
        assertEquals(QuotaResult.Ok(QuotaStatus(7, 1_800_000_000_000L, "FREE")), client().status())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/v1/quota", request.path)
        assertEquals("Bearer test-id-token", request.getHeader(AuthHeaders.AUTHORIZATION))
        assertEquals("test-app-check", request.getHeader(AuthHeaders.APP_CHECK))
    }

    @Test
    fun `an ISO reset time parses too`() = runBlocking {
        server.enqueue(json(200, """{"ai_left_today":150,"resets_at":"2027-01-15T08:00:00Z","tier":"PRO"}"""))
        assertEquals(QuotaResult.Ok(QuotaStatus(150, 1_800_000_000_000L, "PRO")), client().status())
    }

    @Test
    fun `a malformed body or a server error is unavailable`() = runBlocking {
        server.enqueue(json(200, """{"tier":"FREE"}"""))
        assertEquals(QuotaResult.Err(AiError.Unavailable), client().status())
        server.enqueue(json(500, "{}"))
        assertEquals(QuotaResult.Err(AiError.Unavailable), client().status())
    }

    @Test
    fun `no base url or no tokens sends nothing`() = runBlocking {
        assertEquals(QuotaResult.Err(AiError.NotConfigured), client(baseUrl = "").status())
        tokens.state = TokenState.NotConfigured
        assertEquals(QuotaResult.Err(AiError.NotConfigured), client().status())
        tokens.state = TokenState.Failed(retryable = true)
        assertEquals(QuotaResult.Err(AiError.Offline), client().status())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `quota status totals follow the tier`() {
        assertEquals(10, QuotaStatus(3, 0L, "FREE").dailyTotal)
        assertEquals(150, QuotaStatus(3, 0L, "PRO").dailyTotal)
        assertEquals(10, QuotaStatus(3, 0L, "other").dailyTotal)
    }
}
