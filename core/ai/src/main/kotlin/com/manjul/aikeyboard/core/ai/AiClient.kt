/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ai

import com.manjul.aikeyboard.core.config.ConfigKeys
import com.manjul.aikeyboard.core.config.RemoteConfig
import java.io.IOException
import java.io.InterruptedIOException
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * The kb-api client (TRD §4.4, §5.3). [stream] posts an [AiRequest] to `/v1/rewrite` or `/v1/polish` and turns
 * the SSE reply into [AiEvent]s:
 * - input over [MAX_INPUT_CHARS] fails with [AiError.TooLong] before any token is fetched or byte is sent;
 * - the auth headers come from [tokens] ([AuthHeaders.ALLOWED] only); `NotConfigured` sends nothing;
 * - [AiEvent.StillWorking] once after `ai_still_working_ms` with no delta, [AiError.Timeout] after
 *   `ai_timeout_ms` with no delta, before the first one or between two ([config]);
 * - HTTP errors, stream `error` events and `IOException`s map to [AiError] (see [mapHttp]);
 * - cancelling the collection cancels the OkHttp call, which closes the connection.
 *
 * Request and response text never reaches a log or an exception message. [clock] (epoch ms) is used only for
 * the quota reset fallback. The blocking read runs on [io]; the timers run in the collector's context.
 * The [http] client should not add auth headers itself (no `AuthHeadersInterceptor`), or tokens are fetched twice.
 */
class AiClient(
    private val http: OkHttpClient,
    baseUrl: String,
    private val tokens: TokenSource,
    private val config: RemoteConfig,
    private val clock: () -> Long,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val base = baseUrl.trim().toHttpUrlOrNull()

    fun stream(req: AiRequest): Flow<AiEvent> = flow {
        if (req.input.length > MAX_INPUT_CHARS) throw AiException(AiError.TooLong)
        val url = base?.resolve(pathOf(req)) ?: throw AiException(AiError.NotConfigured)
        val headers = AuthHeaders.resolve(tokens.headers())
        val request = Request.Builder()
            .url(url)
            .header("Accept", EVENT_STREAM)
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .post(bodyOf(req).toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val call = http.newCall(request)
        val stillWorkingMs = config.long(ConfigKeys.AI_STILL_WORKING_MS)
        val timeoutMs = config.long(ConfigKeys.AI_TIMEOUT_MS)

        coroutineScope {
            val incoming = Channel<Incoming>(Channel.UNLIMITED)
            val reader = launch(io) { read(call, incoming) }
            try {
                var sawDelta = false
                var stillWorkingSent = false
                while (true) {
                    val wait = when {
                        sawDelta -> timeoutMs
                        !stillWorkingSent && stillWorkingMs < timeoutMs -> stillWorkingMs
                        stillWorkingSent -> timeoutMs - stillWorkingMs
                        else -> timeoutMs
                    }
                    val received = withTimeoutOrNull(wait) { incoming.receiveCatching() }
                    if (received == null) {
                        if (!sawDelta && !stillWorkingSent && stillWorkingMs < timeoutMs) {
                            stillWorkingSent = true
                            emit(AiEvent.StillWorking)
                            continue
                        }
                        throw AiException(AiError.Timeout)
                    }
                    when (val item = received.getOrNull() ?: Incoming.End) {
                        is Incoming.Fail -> throw AiException(item.error)
                        Incoming.End -> throw AiException(AiError.Unavailable)
                        is Incoming.Frame -> when (item.event.event) {
                            "delta" -> {
                                emit(AiEvent.Delta(parseDelta(item.event.data)))
                                sawDelta = true
                            }
                            "done" -> {
                                emit(parseDone(item.event.data))
                                return@coroutineScope
                            }
                            "error" -> throw AiException(streamError(item.event.data))
                            else -> Unit
                        }
                    }
                }
            } finally {
                call.cancel()
                reader.cancel()
            }
        }
    }

    private sealed interface Incoming {
        class Frame(val event: SseParser.Event) : Incoming
        class Fail(val error: AiError) : Incoming
        data object End : Incoming
    }

    /** Blocking: executes [call] and forwards each SSE event, then [Incoming.End] or one [Incoming.Fail]. */
    private suspend fun read(call: Call, out: SendChannel<Incoming>) {
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    out.send(Incoming.Fail(mapHttp(response)))
                    return
                }
                val source = response.body?.source() ?: run {
                    out.send(Incoming.Fail(AiError.Unavailable))
                    return
                }
                val parser = SseParser()
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    for (event in parser.feed(line + "\n")) out.send(Incoming.Frame(event))
                }
                out.send(Incoming.End)
            }
        } catch (e: AiException) {
            out.trySend(Incoming.Fail(e.error))
        } catch (e: InterruptedIOException) {
            // OkHttp's own read/call timeout, or a cancel; our timers normally fire first.
            out.trySend(Incoming.Fail(AiError.Timeout))
        } catch (e: IOException) {
            out.trySend(Incoming.Fail(AiError.Offline))
        } catch (e: RuntimeException) {
            out.trySend(Incoming.Fail(AiError.Unavailable))
        } finally {
            out.close()
        }
    }

    /** Maps a non-2xx reply. The body is read only for the error code and its numbers, never kept or logged. */
    internal fun mapHttp(response: Response): AiError {
        val body = runCatching { response.peekBody(MAX_ERROR_BODY_BYTES).string() }.getOrNull()
        val json = body?.let(::parseObject)
        val code = json?.string("error")
        return when (response.code) {
            400 -> if (code == "too_long") AiError.TooLong else AiError.Unavailable
            402 -> AiError.Quota(json?.string("resets_at")?.let(::parseInstant) ?: nextUtcMidnight())
            429 -> AiError.RateLimited(retryAfter(response, json))
            451 -> AiError.Refused
            503 -> when (code) {
                "ai_paused" -> AiError.Paused
                "overloaded" -> AiError.RateLimited(retryAfter(response, json))
                else -> AiError.Unavailable
            }
            else -> AiError.Unavailable
        }
    }

    private fun retryAfter(response: Response, json: JsonObject?): Long =
        response.header("Retry-After")?.trim()?.toLongOrNull()?.takeIf { it >= 0 }
            ?: (json?.get("retry_after") as? JsonPrimitive)?.longOrNull?.takeIf { it >= 0 }
            ?: DEFAULT_RETRY_AFTER_S

    private fun nextUtcMidnight(): Long =
        Instant.ofEpochMilli(clock()).atZone(ZoneOffset.UTC).toLocalDate().plusDays(1)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun parseInstant(value: String): Long? = runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()

    private fun parseDelta(data: String): String =
        parseObject(data)?.string("text") ?: throw AiException(AiError.Unavailable)

    private fun parseDone(data: String): AiEvent.Done {
        val json = parseObject(data) ?: throw AiException(AiError.Unavailable)
        return AiEvent.Done(
            quotaLeft = (json["quota_left"] as? JsonPrimitive)?.intOrNull ?: -1,
            trimmed = (json["trimmed"] as? JsonPrimitive)?.booleanOrNull ?: false,
            polished = json.string("polished"),
            translation = json.string("translation"),
            sourceLangDetected = json.string("source_lang_detected"),
        )
    }

    private fun streamError(data: String): AiError = when (parseObject(data)?.string("error")) {
        "refused" -> AiError.Refused
        "overloaded" -> AiError.RateLimited(DEFAULT_RETRY_AFTER_S)
        "ai_paused" -> AiError.Paused
        else -> AiError.Unavailable
    }

    private fun parseObject(text: String): JsonObject? =
        runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    companion object {
        /** Client-side input cap (TRD §4.4.2; the server allows 4,000). */
        const val MAX_INPUT_CHARS = 2_000

        const val REWRITE_PATH = "v1/rewrite"
        const val POLISH_PATH = "v1/polish"

        private const val EVENT_STREAM = "text/event-stream"
        private const val DEFAULT_RETRY_AFTER_S = 5L
        private const val MAX_ERROR_BODY_BYTES = 4_096L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun pathOf(req: AiRequest): String = when (req) {
            is AiRequest.Rewrite -> REWRITE_PATH
            is AiRequest.Polish -> POLISH_PATH
        }

        internal fun bodyOf(req: AiRequest): JsonElement = when (req) {
            is AiRequest.Rewrite -> buildJsonObject {
                put("mode", req.mode.wire)
                put("locale", req.locale)
                put("text", req.text)
                req.targetLang?.let { put("target_lang", it) }
                put("client_req_id", req.clientReqId)
            }
            is AiRequest.Polish -> buildJsonObject {
                put("transcript", req.transcript)
                req.sourceLang?.let { put("source_lang", it) }
                put("target_lang", req.targetLang)
                put("script", req.script.wire)
                put("client_req_id", req.clientReqId)
            }
        }
    }
}
