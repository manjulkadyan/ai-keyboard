/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ai.kb

import com.manjul.aikeyboard.core.ai.AiError
import com.manjul.aikeyboard.core.ai.AuthHeaders
import com.manjul.aikeyboard.core.ai.TokenSource
import com.manjul.aikeyboard.core.ai.TokenState
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * The user's AI quota for today, as kb-api reports it. The server owns the quota (TRD §5.3, the `ai_usage_local`
 * row: display only), so the app never derives a limit from the tier. [dailyTotal] is the optional
 * `ai_daily_limit` of the answer, and null when the server does not send it.
 */
data class QuotaStatus(
    val aiLeftToday: Int,
    val resetsAtMs: Long,
    val tier: String,
    val dailyTotal: Int? = null,
)

/** The answer of [QuotaClient.status]. `:core:ai` is pure JVM, so this does not use `Outcome`. */
sealed interface QuotaResult {
    data class Ok(val status: QuotaStatus) : QuotaResult

    data class Err(val error: AiError) : QuotaResult
}

/**
 * `GET /v1/quota` on kb-api (Settings → AI): `{ai_left_today, resets_at, tier, ai_daily_limit?}`. Only the auth
 * headers are sent. Cancelling the caller interrupts the blocking call, which OkHttp turns into a closed call.
 * The caller must ask only after `consentAiAt` is set. The auth headers are credentials: they never reach a log, an
 * exception or a result. Every failure maps to an [AiError] without a message.
 */
class QuotaClient(
    private val http: OkHttpClient,
    baseUrl: String,
    private val tokens: TokenSource,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val base = baseUrl.trim().takeIf { it.isNotEmpty() }?.toHttpUrlOrNull()

    suspend fun status(): QuotaResult {
        val url = base?.resolve(QUOTA_PATH) ?: return QuotaResult.Err(AiError.NotConfigured)
        val headers = when (val state = tokens.headers()) {
            is TokenState.Ready -> AuthHeaders.filter(state.headers)
            TokenState.NotConfigured -> return QuotaResult.Err(AiError.NotConfigured)
            is TokenState.Failed -> return QuotaResult.Err(
                if (state.retryable) AiError.Offline else AiError.Unavailable,
            )
        }
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .get()
            .build()
        return runInterruptible(io) {
            try {
                http.newCall(request).execute().use(::parse)
            } catch (e: IOException) {
                QuotaResult.Err(AiError.Offline)
            } catch (e: RuntimeException) {
                QuotaResult.Err(AiError.Unavailable)
            }
        }
    }

    private fun parse(response: Response): QuotaResult {
        if (!response.isSuccessful) return QuotaResult.Err(AiError.Unavailable)
        val text = runCatching { response.body?.string() }.getOrNull()
        val json = text?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
        val status = json?.let(::statusOf) ?: return QuotaResult.Err(AiError.Unavailable)
        return QuotaResult.Ok(status)
    }

    private fun statusOf(json: JsonObject): QuotaStatus? {
        val left = (json["ai_left_today"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull ?: return null
        val resetsAt = timeOf(json["resets_at"]) ?: return null
        val tier = (json["tier"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: DEFAULT_TIER
        val limit = (json["ai_daily_limit"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
        return QuotaStatus(left.coerceAtLeast(0), resetsAt, tier, limit?.takeIf { it > 0 })
    }

    /** Epoch ms as a number, or an ISO-8601 instant string. */
    private fun timeOf(element: Any?): Long? {
        val p = element as? JsonPrimitive ?: return null
        if (!p.isString) return p.longOrNull
        return runCatching { Instant.parse(p.content).toEpochMilli() }.getOrNull()
    }

    companion object {
        const val QUOTA_PATH = "v1/quota"
        private const val DEFAULT_TIER = "FREE"
    }
}
