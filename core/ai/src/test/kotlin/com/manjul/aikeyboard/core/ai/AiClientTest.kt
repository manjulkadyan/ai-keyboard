/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ai

import com.manjul.aikeyboard.core.config.ConfigKeys
import com.manjul.aikeyboard.core.config.DefaultsRemoteConfig
import java.net.ServerSocket
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy

class AiClientTest {
    private lateinit var server: MockWebServer
    private val tokens = FakeTokenSource()
    private val http = OkHttpClient()
    private val now = Instant.parse("2026-10-26T15:30:00Z").toEpochMilli()

    private fun client(baseUrl: String = server.url("/").toString()) =
        AiClient(http, baseUrl, tokens, DefaultsRemoteConfig(), clock = { now })

    private fun rewrite(text: String = "helo wrld", mode: RewriteMode = RewriteMode.FIX, target: String? = null) =
        AiRequest.Rewrite(mode, "en", text, target, "req-1")

    private fun sse(vararg frames: String) = MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setBody(frames.joinToString(""))

    private fun frame(event: String, data: String) = "event: $event\ndata: $data\n\n"

    private fun errorResponse(code: Int, error: String) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"error":"$error"}""")

    private fun failure(request: AiRequest = rewrite()): AiException = assertFailsWith {
        runBlocking { client().stream(request).toList() }
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

    @Test
    fun `rewrite streams deltas then done`() = runBlocking {
        server.enqueue(
            sse(
                frame("delta", """{"text":"Hello"}"""),
                frame("delta", """{"text":" world"}"""),
                frame("done", """{"quota_left":7,"trimmed":false,"prompt":"rewrite@1"}"""),
            ),
        )

        val events = client().stream(rewrite()).toList()

        assertEquals(
            listOf(AiEvent.Delta("Hello"), AiEvent.Delta(" world"), AiEvent.Done(7, false, null, null, null)),
            events,
        )
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/v1/rewrite", recorded.path)
        assertEquals("test-app-check", recorded.getHeader(AuthHeaders.APP_CHECK))
        assertEquals("Bearer test-id-token", recorded.getHeader(AuthHeaders.AUTHORIZATION))
        assertEquals("text/event-stream", recorded.getHeader("Accept"))
        val body = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertEquals("fix", body["mode"]!!.jsonPrimitive.content)
        assertEquals("en", body["locale"]!!.jsonPrimitive.content)
        assertEquals("helo wrld", body["text"]!!.jsonPrimitive.content)
        assertEquals("req-1", body["client_req_id"]!!.jsonPrimitive.content)
        assertFalse("target_lang" in body)
    }

    @Test
    fun `translate rewrite sends the target language`() = runBlocking {
        server.enqueue(sse(frame("done", """{"quota_left":1,"trimmed":true}""")))

        val events = client().stream(rewrite(mode = RewriteMode.TRANSLATE, target = "es")).toList()

        assertEquals(listOf<AiEvent>(AiEvent.Done(1, true, null, null, null)), events)
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("translate", body["mode"]!!.jsonPrimitive.content)
        assertEquals("es", body["target_lang"]!!.jsonPrimitive.content)
    }

    @Test
    fun `polish posts to v1 polish and parses the done payload`() = runBlocking {
        server.enqueue(
            sse(
                frame("delta", """{"text":"{\"polished\":"}"""),
                frame(
                    "done",
                    """{"polished":"Hi there.","translation":"Hola.","source_lang_detected":"en","quota_left":3,"prompt":"polish_translate@1"}""",
                ),
            ),
        )
        val request = AiRequest.Polish("hi there", "en", "es", OutputScript.ROMAN, "req-2")

        val events = client().stream(request).toList()

        assertEquals(AiEvent.Done(3, false, "Hi there.", "Hola.", "en"), events.last())
        val recorded = server.takeRequest()
        assertEquals("/v1/polish", recorded.path)
        val body = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertEquals("hi there", body["transcript"]!!.jsonPrimitive.content)
        assertEquals("en", body["source_lang"]!!.jsonPrimitive.content)
        assertEquals("es", body["target_lang"]!!.jsonPrimitive.content)
        assertEquals("ROMAN", body["script"]!!.jsonPrimitive.content)
        assertEquals("req-2", body["client_req_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `polish without a source language omits it`() = runBlocking {
        server.enqueue(sse(frame("done", """{"polished":"a","translation":"b","quota_left":0}""")))

        client().stream(AiRequest.Polish("a", null, "en", OutputScript.MATCH_INPUT, "req-3")).toList()

        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertTrue(body["source_lang"] == null || body["source_lang"] == JsonNull)
        assertEquals("MATCH_INPUT", body["script"]!!.jsonPrimitive.content)
    }

    @Test
    fun `402 maps to Quota with resets_at`() {
        server.enqueue(
            MockResponse().setResponseCode(402).setBody("""{"error":"quota","resets_at":"2026-10-27T00:00:00Z"}"""),
        )
        assertEquals(AiError.Quota(Instant.parse("2026-10-27T00:00:00Z").toEpochMilli()), failure().error)
    }

    @Test
    fun `402 without resets_at falls back to the next UTC midnight`() {
        server.enqueue(MockResponse().setResponseCode(402).setBody("""{"error":"quota"}"""))
        assertEquals(AiError.Quota(Instant.parse("2026-10-27T00:00:00Z").toEpochMilli()), failure().error)
    }

    @Test
    fun `429 maps to RateLimited from Retry-After`() {
        server.enqueue(
            MockResponse().setResponseCode(429).setHeader("Retry-After", "7").setBody("""{"error":"rate_limited"}"""),
        )
        assertEquals(AiError.RateLimited(7), failure().error)
    }

    @Test
    fun `429 without a header reads retry_after from the body`() {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"rate_limited","retry_after":12}"""))
        assertEquals(AiError.RateLimited(12), failure().error)
    }

    @Test
    fun `451 maps to Refused`() {
        server.enqueue(errorResponse(451, "refused"))
        assertEquals(AiError.Refused, failure().error)
    }

    @Test
    fun `503 ai_paused maps to Paused`() {
        server.enqueue(errorResponse(503, "ai_paused"))
        assertEquals(AiError.Paused, failure().error)
    }

    @Test
    fun `503 overloaded maps to RateLimited`() {
        server.enqueue(errorResponse(503, "overloaded").setHeader("Retry-After", "5"))
        assertEquals(AiError.RateLimited(5), failure().error)
    }

    @Test
    fun `400 too_long maps to TooLong and other 400s to Unavailable`() {
        server.enqueue(errorResponse(400, "too_long"))
        assertEquals(AiError.TooLong, failure().error)
        server.enqueue(errorResponse(400, "bad_request"))
        assertEquals(AiError.Unavailable, failure().error)
    }

    @Test
    fun `401, 403, 500 and a non-JSON body map to Unavailable`() {
        server.enqueue(errorResponse(401, "app_check"))
        server.enqueue(errorResponse(403, "integrity"))
        server.enqueue(errorResponse(500, "internal"))
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>bad gateway</html>"))
        repeat(4) { assertEquals(AiError.Unavailable, failure().error) }
    }

    @Test
    fun `an error event after deltas fails the flow`() = runBlocking {
        server.enqueue(sse(frame("delta", """{"text":"Hi"}"""), frame("error", """{"error":"refused"}""")))
        val seen = mutableListOf<AiEvent>()

        val e = assertFailsWith<AiException> { client().stream(rewrite()).collect { seen += it } }

        assertEquals(AiError.Refused, e.error)
        assertEquals(listOf<AiEvent>(AiEvent.Delta("Hi")), seen)
    }

    @Test
    fun `a stream that ends without done, or with a malformed frame, is Unavailable`() {
        server.enqueue(sse(frame("delta", """{"text":"Hi"}""")))
        assertEquals(AiError.Unavailable, failure().error)
        server.enqueue(sse(frame("delta", """{"nope":1}""")))
        assertEquals(AiError.Unavailable, failure().error)
        server.enqueue(sse(frame("done", "not json")))
        assertEquals(AiError.Unavailable, failure().error)
    }

    @Test
    fun `connection refused maps to Offline`() {
        val url = server.url("/").toString()
        server.shutdown()
        val e = assertFailsWith<AiException> { runBlocking { client(url).stream(rewrite()).toList() } }
        assertEquals(AiError.Offline, e.error)
    }

    @Test
    fun `a dropped connection mid-stream maps to Offline`() {
        server.enqueue(
            sse(frame("delta", """{"text":"Hi"}"""), frame("delta", """{"text":"there"}"""))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )
        assertEquals(AiError.Offline, failure().error)
    }

    @Test
    fun `no bytes for 10 s gives StillWorking at 4 s, then Timeout`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val seen = mutableListOf<Pair<Long, AiEvent>>()

        val e = assertFailsWith<AiException> { client().stream(rewrite()).collect { seen += currentTime to it } }

        assertEquals(AiError.Timeout, e.error)
        assertEquals(listOf<Pair<Long, AiEvent>>(4_000L to AiEvent.StillWorking), seen)
        assertEquals(10_000L, currentTime)
    }

    @Test
    fun `the timeout applies between deltas too`() = runBlocking {
        // Real time: the first delta comes from a real socket, so virtual time would race it.
        val socket = HangingSseServer(firstFrame = frame("delta", """{"text":"Hi"}"""))
        val config = DefaultsRemoteConfig(mapOf(ConfigKeys.AI_STILL_WORKING_MS to 3_000L, ConfigKeys.AI_TIMEOUT_MS to 4_000L))
        val seen = mutableListOf<Pair<Long, AiEvent>>()
        val start = System.nanoTime()
        fun elapsedMs() = (System.nanoTime() - start) / 1_000_000
        try {
            val c = AiClient(http, socket.baseUrl, tokens, config, clock = { now })
            val e = assertFailsWith<AiException> { c.stream(rewrite()).collect { seen += elapsedMs() to it } }
            assertEquals(AiError.Timeout, e.error)
        } finally {
            socket.close()
        }
        assertEquals(listOf<AiEvent>(AiEvent.Delta("Hi")), seen.map { it.second })
        assertTrue(elapsedMs() - seen.single().first >= 4_000L, "timed out before the idle timeout")
    }

    @Test
    fun `timeouts come from the remote config`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val config = DefaultsRemoteConfig(mapOf(ConfigKeys.AI_STILL_WORKING_MS to 1_000L, ConfigKeys.AI_TIMEOUT_MS to 2_000L))
        val c = AiClient(http, server.url("/").toString(), tokens, config, clock = { now })
        val seen = mutableListOf<Long>()

        assertFailsWith<AiException> { c.stream(rewrite()).collect { seen += currentTime } }

        assertEquals(listOf(1_000L), seen)
        assertEquals(2_000L, currentTime)
    }

    @Test
    fun `input over 2,000 chars is TooLong and sends nothing`() {
        val e = failure(rewrite(text = "a".repeat(2_001)))
        assertEquals(AiError.TooLong, e.error)
        val polish = failure(AiRequest.Polish("b".repeat(2_001), null, "en", OutputScript.MATCH_INPUT, "p"))
        assertEquals(AiError.TooLong, polish.error)
        assertEquals(0, server.requestCount)
        assertEquals(0, tokens.headerCalls)
    }

    @Test
    fun `exactly 2,000 chars is sent`() = runBlocking {
        server.enqueue(sse(frame("done", """{"quota_left":1}""")))
        client().stream(rewrite(text = "a".repeat(2_000))).toList()
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `NotConfigured and failed tokens send nothing`() {
        tokens.state = TokenState.NotConfigured
        assertEquals(AiError.NotConfigured, failure().error)
        tokens.state = TokenState.Failed(retryable = true)
        assertEquals(AiError.Unavailable, failure().error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `only auth headers from the token source are sent`() = runBlocking {
        tokens.state = TokenState.Ready(mapOf(AuthHeaders.DEV_UID to "dev-1", "X-Install-Id" to "abc"))
        server.enqueue(sse(frame("done", """{"quota_left":1}""")))

        client().stream(rewrite()).toList()

        val recorded = server.takeRequest()
        assertEquals("dev-1", recorded.getHeader(AuthHeaders.DEV_UID))
        assertNull(recorded.getHeader("X-Install-Id"))
    }

    @Test
    fun `cancelling the flow closes the server connection`() = runBlocking {
        val socket = HangingSseServer(firstFrame = frame("delta", """{"text":"Hi"}"""))
        try {
            val c = AiClient(http, socket.baseUrl, tokens, DefaultsRemoteConfig(), clock = { now })
            assertEquals(AiEvent.Delta("Hi"), c.stream(rewrite()).first())
            assertTrue(socket.closed.await(5, TimeUnit.SECONDS), "the server never saw the connection close")
        } finally {
            socket.close()
        }
    }

    @Test
    fun `AiException message never contains the request text`() {
        val secret = "MY-SECRET-PROMPT-TEXT"
        val responses = listOf(
            errorResponse(400, "too_long"),
            MockResponse().setResponseCode(400).setBody("""{"error":"bad_request","echo":"$secret"}"""),
            MockResponse().setResponseCode(402).setBody("""{"error":"quota","resets_at":"$secret"}"""),
            MockResponse().setResponseCode(429).setHeader("Retry-After", secret),
            errorResponse(451, "refused"),
            MockResponse().setResponseCode(500).setBody(secret),
            sse(frame("delta", """{"text":"$secret"}"""), frame("error", """{"error":"$secret"}""")),
            sse(frame("done", secret)),
        )
        for (response in responses) {
            server.enqueue(response)
            val e = failure(rewrite(text = secret))
            val message = e.message.orEmpty()
            assertEquals(e.error::class.java.simpleName, message)
            assertFalse(e.toString().contains(secret), e.toString())
            assertNull(e.cause)
        }
        val tooLong = failure(rewrite(text = secret.repeat(200)))
        assertFalse(tooLong.toString().contains(secret))
    }

    @Test
    fun `requests, events and errors print no user text`() {
        val secret = "MY-SECRET"
        val printed = listOf(
            rewrite(text = secret, target = "es"),
            AiRequest.Polish(secret, "en", "es", OutputScript.NATIVE, "p"),
            AiEvent.Delta(secret),
            AiEvent.Done(1, false, secret, secret, "en"),
        ).joinToString { it.toString() }
        assertFalse(printed.contains(secret), printed)
    }

    @Test
    fun `wire names match the kb-api contract`() {
        assertEquals(
            listOf("fix", "formal", "friendly", "shorter", "longer", "emojify", "translate"),
            RewriteMode.entries.map { it.wire },
        )
        assertEquals(listOf("MATCH_INPUT", "ROMAN", "NATIVE"), OutputScript.entries.map { it.wire })
    }

    @Test
    fun `CloudAiEngine delegates to the client`() = runBlocking {
        server.enqueue(sse(frame("delta", """{"text":"x"}"""), frame("done", """{"quota_left":2}""")))
        val engine: AiEngine = CloudAiEngine(client())
        assertEquals("cloud", engine.name)
        assertEquals(AiEvent.Done(2, false, null, null, null), engine.stream(rewrite()).toList().last())
    }

    /** Sends HTTP headers and [firstFrame], then holds the connection open until the client closes it. */
    private class HangingSseServer(firstFrame: String) : AutoCloseable {
        private val socket = ServerSocket(0)
        val baseUrl = "http://127.0.0.1:${socket.localPort}/"
        val closed = CountDownLatch(1)

        init {
            thread(isDaemon = true) {
                runCatching {
                    socket.accept().use { conn ->
                        val input = conn.getInputStream().bufferedReader()
                        var contentLength = 0
                        while (true) {
                            val line = input.readLine() ?: break
                            if (line.isEmpty()) break
                            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                                contentLength = line.substringAfter(':').trim().toInt()
                            }
                        }
                        repeat(contentLength) { input.read() }
                        val out = conn.getOutputStream()
                        out.write(
                            ("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n" + firstFrame)
                                .toByteArray(),
                        )
                        out.flush()
                        // Blocks until the client closes the socket (read returns -1 or throws).
                        runCatching { conn.getInputStream().read() }
                        closed.countDown()
                    }
                }
            }
        }

        override fun close() {
            socket.close()
        }
    }
}
