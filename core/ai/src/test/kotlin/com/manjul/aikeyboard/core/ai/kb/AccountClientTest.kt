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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy

class AccountClientTest {
    private lateinit var server: MockWebServer
    private val tokens = FakeTokenSource()
    private val http = OkHttpClient()

    private fun client(baseUrl: String = server.url("/").toString()) = AccountClient(http, baseUrl, tokens)

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
    fun `200 posts only client_req_id with the auth headers`() = runBlocking {
        server.enqueue(json(200, """{"deleted":true}"""))
        assertEquals(AccountResult.Deleted, client().delete("req-1"))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/account/delete", request.path)
        assertEquals("Bearer test-id-token", request.getHeader(AuthHeaders.AUTHORIZATION))
        assertEquals("test-app-check", request.getHeader(AuthHeaders.APP_CHECK))
        val body = Json.parseToJsonElement(request.body.readUtf8()) as JsonObject
        assertEquals(setOf("client_req_id"), body.keys)
        assertEquals(JsonPrimitive("req-1"), body["client_req_id"])
    }

    @Test
    fun `401 is an error`() = runBlocking {
        server.enqueue(json(401, """{"error":"unauthenticated"}"""))
        assertEquals(AccountResult.Err(AiError.Unavailable), client().delete("req-1"))
    }

    @Test
    fun `200 without deleted true is an error`() = runBlocking {
        server.enqueue(json(200, """{"deleted":false}"""))
        assertEquals(AccountResult.Err(AiError.Unavailable), client().delete("req-1"))
    }

    @Test
    fun `503 and 500 are errors`() = runBlocking {
        server.enqueue(json(503, "{}"))
        server.enqueue(json(500, "{}"))
        assertEquals(AccountResult.Err(AiError.Unavailable), client().delete("req-1"))
        assertEquals(AccountResult.Err(AiError.Unavailable), client().delete("req-2"))
    }

    @Test
    fun `a dropped connection is offline`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertEquals(AccountResult.Err(AiError.Offline), client().delete("req-1"))
    }

    @Test
    fun `no token sends nothing`() = runBlocking {
        tokens.state = TokenState.NotConfigured
        assertEquals(AccountResult.Err(AiError.NotConfigured), client().delete("req-1"))
        tokens.state = TokenState.Failed(retryable = true)
        assertEquals(AccountResult.Err(AiError.Offline), client().delete("req-1"))
        tokens.state = TokenState.Failed(retryable = false)
        assertEquals(AccountResult.Err(AiError.Unavailable), client().delete("req-1"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `no base url sends nothing`() = runBlocking {
        assertEquals(AccountResult.Err(AiError.NotConfigured), client(baseUrl = " ").delete("req-1"))
        assertEquals(0, tokens.headerCalls)
    }

    @Test
    fun `an empty or too long request id is refused before sending`() = runBlocking {
        assertEquals(AccountResult.Err(AiError.Unavailable), client().delete(""))
        assertEquals(AccountResult.Err(AiError.Unavailable), client().delete("x".repeat(65)))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `results never print a token`() {
        val text = listOf(AccountResult.Deleted, AccountResult.Err(AiError.Offline)).joinToString()
        assertFalse("Bearer" in text)
        assertTrue(AccountClient.newRequestId().length in 1..64)
    }
}
