/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ai.kb

import com.manjul.aikeyboard.core.ai.AuthHeaders
import com.manjul.aikeyboard.core.ai.FakeTokenSource
import com.manjul.aikeyboard.core.ai.TokenState
import java.net.ServerSocket
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class EntitlementClientTest {
    private lateinit var server: MockWebServer
    private val tokens = FakeTokenSource()
    private val http = OkHttpClient()

    private fun client(baseUrl: String = server.url("/").toString()) = EntitlementClient(http, baseUrl, tokens)

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
    fun `200 parses all fields`() = runBlocking {
        server.enqueue(
            json(
                200,
                """{"pro_active":true,"state":"grace","expires_at":1800000000000,""" +
                    """"issued_at":1799990000000,"signature":"c2ln"}""",
            ),
        )
        val result = client().verify("tok-1", "pro")
        assertEquals(
            EntitlementResult.Ok(Entitlement(true, SubState.GRACE, 1_800_000_000_000L, 1_799_990_000_000L, "c2ln")),
            result,
        )
    }

    @Test
    fun `ISO-8601 times are accepted too`() = runBlocking {
        server.enqueue(
            json(
                200,
                """{"pro_active":true,"state":"active","expires_at":"2027-01-15T08:00:00Z",""" +
                    """"issued_at":"2026-11-07T10:00:00Z","signature":"s"}""",
            ),
        )
        val e = (client().current() as EntitlementResult.Ok).entitlement
        assertEquals(java.time.Instant.parse("2027-01-15T08:00:00Z").toEpochMilli(), e.expiresAtMs)
        assertEquals(java.time.Instant.parse("2026-11-07T10:00:00Z").toEpochMilli(), e.issuedAtMs)
    }

    @Test
    fun `200 without state maps from pro_active`() = runBlocking {
        server.enqueue(json(200, """{"pro_active":true,"expires_at":5,"signature":"s"}"""))
        server.enqueue(json(200, """{"pro_active":false,"expires_at":0,"signature":"s"}"""))
        val active = (client().current() as EntitlementResult.Ok).entitlement
        val none = (client().current() as EntitlementResult.Ok).entitlement
        assertEquals(SubState.ACTIVE, active.state)
        assertEquals(0L, active.issuedAtMs)
        assertEquals(SubState.NONE, none.state)
        assertFalse(none.proActive)
    }

    @Test
    fun `an unknown state string maps from pro_active`() = runBlocking {
        server.enqueue(json(200, """{"pro_active":false,"state":"future_state","expires_at":0,"signature":"s"}"""))
        assertEquals(SubState.NONE, (client().current() as EntitlementResult.Ok).entitlement.state)
    }

    @Test
    fun `400 invalid_token gives INVALID_TOKEN`() = runBlocking {
        server.enqueue(json(400, """{"error":"invalid_token"}"""))
        assertEquals(EntitlementResult.Err(EntitlementError.INVALID_TOKEN), client().verify("tok-1", "pro"))
    }

    @Test
    fun `other errors give UNAVAILABLE`() = runBlocking {
        server.enqueue(json(500, """{"error":"internal"}"""))
        server.enqueue(json(401, """{"error":"unauthorized"}"""))
        server.enqueue(json(200, """not json"""))
        server.enqueue(json(200, """{"state":"active"}"""))
        repeat(4) { assertEquals(EntitlementResult.Err(EntitlementError.UNAVAILABLE), client().current(), "call $it") }
    }

    @Test
    fun `socket refused gives OFFLINE`() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        assertEquals(
            EntitlementResult.Err(EntitlementError.OFFLINE),
            client("http://127.0.0.1:$port/").verify("tok-1", "pro"),
        )
    }

    @Test
    fun `no base url or no auth gives NOT_CONFIGURED and sends nothing`() = runBlocking {
        assertEquals(EntitlementResult.Err(EntitlementError.NOT_CONFIGURED), client("").current())
        tokens.state = TokenState.NotConfigured
        assertEquals(EntitlementResult.Err(EntitlementError.NOT_CONFIGURED), client().current())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a token fetch failure gives OFFLINE when retryable, else UNAVAILABLE`() = runBlocking {
        tokens.state = TokenState.Failed(retryable = true)
        assertEquals(EntitlementResult.Err(EntitlementError.OFFLINE), client().current())
        tokens.state = TokenState.Failed(retryable = false)
        assertEquals(EntitlementResult.Err(EntitlementError.UNAVAILABLE), client().current())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `verify posts purchase_token and product_id with the auth headers`() = runBlocking {
        server.enqueue(json(200, """{"pro_active":true,"expires_at":5,"signature":"s"}"""))
        client().verify("tok-1", "pro")
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/v1/entitlement/verify", req.path)
        val body = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals(setOf("purchase_token", "product_id"), body.keys)
        assertEquals("tok-1", body.getValue("purchase_token").jsonPrimitive.content)
        assertEquals("pro", body.getValue("product_id").jsonPrimitive.content)
        assertEquals("test-app-check", req.getHeader(AuthHeaders.APP_CHECK))
        assertEquals("Bearer test-id-token", req.getHeader(AuthHeaders.AUTHORIZATION))
    }

    @Test
    fun `current is a GET on v1 entitlement`() = runBlocking {
        server.enqueue(json(200, """{"pro_active":false,"expires_at":0,"signature":"s"}"""))
        client().current()
        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals("/v1/entitlement", req.path)
    }

    @Test
    fun `toString never shows the signature`() {
        val e = Entitlement(true, SubState.ACTIVE, 1L, 2L, "SIGNATURE-VALUE")
        assertFalse("SIGNATURE-VALUE" in e.toString())
        assertTrue("ACTIVE" in e.toString())
    }

    @Test
    fun `wire strings`() {
        assertEquals(
            listOf("none", "pending", "active", "grace", "hold", "paused", "expired", "revoked"),
            SubState.entries.map { it.wire },
        )
        assertEquals(SubState.ON_HOLD, SubState.fromWire("hold"))
        assertEquals(null, SubState.fromWire("nope"))
    }
}
