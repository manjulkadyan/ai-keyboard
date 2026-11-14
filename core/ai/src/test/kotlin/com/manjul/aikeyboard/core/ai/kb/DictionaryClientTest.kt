/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ai.kb

import com.manjul.aikeyboard.core.ai.AuthHeaders
import com.manjul.aikeyboard.core.ai.FakeTokenSource
import com.manjul.aikeyboard.core.ai.TokenState
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy

class DictionaryClientTest {
    private lateinit var server: MockWebServer
    private val tokens = FakeTokenSource()
    private val http = OkHttpClient()

    private fun client(baseUrl: String = server.url("/").toString(), http: OkHttpClient = this.http) =
        DictionaryClient(http, baseUrl, tokens)

    private fun json(code: Int, body: String) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private val found = """
        {"entries":[
          {"headword":"run","pos":"verb","definition":"To move fast on foot.","example":"Run home.","synonyms":["sprint"]},
          {"headword":"run","pos":"noun","definition":"An act of running.","example":null,"synonyms":[]}
        ],"source":"freedictionaryapi","licence_url":"https://creativecommons.org/licenses/by-sa/4.0/"}
    """.trimIndent()

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
    fun `200 maps to Found and posts word and lang with the auth headers`() = runBlocking {
        server.enqueue(json(200, found))
        val result = client().lookup("run", "en")
        assertEquals(
            DictionaryResult.Found(
                listOf(
                    RemoteEntry("run", "verb", "To move fast on foot.", "Run home.", listOf("sprint")),
                    RemoteEntry("run", "noun", "An act of running.", null, emptyList()),
                ),
                licenceUrl = "https://creativecommons.org/licenses/by-sa/4.0/",
            ),
            result,
        )
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/dictionary", request.path)
        assertEquals("Bearer test-id-token", request.getHeader(AuthHeaders.AUTHORIZATION))
        assertEquals("test-app-check", request.getHeader(AuthHeaders.APP_CHECK))
        val body = Json.parseToJsonElement(request.body.readUtf8()) as JsonObject
        assertEquals(setOf("word", "lang"), body.keys)
        assertEquals(JsonPrimitive("run"), body["word"])
        assertEquals(JsonPrimitive("en"), body["lang"])
    }

    @Test
    fun `an unknown field is ignored`() = runBlocking {
        server.enqueue(json(200, found.replace("\"source\":", "\"extra\":1,\"source\":")))
        assertEquals(2, (client().lookup("run", "en") as DictionaryResult.Found).entries.size)
    }

    @Test
    fun `404 maps to NotFound`() = runBlocking {
        server.enqueue(json(404, """{"error":"not_found"}"""))
        assertEquals(DictionaryResult.NotFound, client().lookup("zzzz", "en"))
    }

    @Test
    fun `503 400 401 429 and 500 map to Unavailable`() = runBlocking {
        listOf(503, 400, 401, 429, 500).forEach { code ->
            server.enqueue(json(code, """{"error":"x"}"""))
            assertEquals(DictionaryResult.Unavailable, client().lookup("run", "en"), "status $code")
        }
    }

    @Test
    fun `a malformed or empty 200 maps to Unavailable`() = runBlocking {
        server.enqueue(json(200, "<html>"))
        server.enqueue(json(200, """{"entries":[],"source":"s","licence_url":"u"}"""))
        assertEquals(DictionaryResult.Unavailable, client().lookup("run", "en"))
        assertEquals(DictionaryResult.Unavailable, client().lookup("run", "en"))
    }

    @Test
    fun `a timeout maps to Unavailable`() = runBlocking {
        server.enqueue(json(200, found).setHeadersDelay(2, TimeUnit.SECONDS))
        val slow = OkHttpClient.Builder().readTimeout(200, TimeUnit.MILLISECONDS).build()
        assertEquals(DictionaryResult.Unavailable, client(http = slow).lookup("run", "en"))
    }

    @Test
    fun `a dropped connection maps to Unavailable`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertEquals(DictionaryResult.Unavailable, client().lookup("run", "en"))
    }

    @Test
    fun `no token maps to Unavailable and sends nothing`() = runBlocking {
        tokens.state = TokenState.NotConfigured
        assertEquals(DictionaryResult.Unavailable, client().lookup("run", "en"))
        tokens.state = TokenState.Failed(retryable = true)
        assertEquals(DictionaryResult.Unavailable, client().lookup("run", "en"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `no base url sends nothing`() = runBlocking {
        assertEquals(DictionaryResult.Unavailable, client(baseUrl = " ").lookup("run", "en"))
        assertEquals(0, tokens.headerCalls)
    }

    @Test
    fun `a blank word sends nothing`() = runBlocking {
        assertEquals(DictionaryResult.NotFound, client().lookup("  ", "en"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `results never print the word or a definition`() {
        val text = DictionaryResult.Found(
            listOf(RemoteEntry("zyxwordleak", "noun", "zyxdefleak", "zyxexleak", listOf("zyxsynleak"))),
            "https://example.com/licence",
        ).toString()
        assertFalse("zyx" in text, text)
    }
}
