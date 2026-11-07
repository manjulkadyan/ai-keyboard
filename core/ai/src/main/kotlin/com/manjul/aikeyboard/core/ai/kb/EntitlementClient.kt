/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ai.kb

import com.manjul.aikeyboard.core.ai.AuthHeaders
import com.manjul.aikeyboard.core.ai.TokenSource
import com.manjul.aikeyboard.core.ai.TokenState
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** The kb-api entitlement calls. [EntitlementClient] is the real one, tests use a fake. */
interface EntitlementApi {
    suspend fun verify(purchaseToken: String, productId: String): EntitlementResult

    suspend fun current(): EntitlementResult
}

/**
 * kb-api entitlement calls (TRD §5.3): `POST /v1/entitlement/verify` with `{purchase_token, product_id}` and
 * `GET /v1/entitlement`. Both answer `{pro_active, expires_at, signature, state?, issued_at?}`. A missing or unknown
 * `state` maps from `pro_active`, so an S3 backend still works (ARCH-006 backwards compatibility).
 *
 * The purchase token and the auth headers are credentials (OPS-001): they never reach a log, an exception or a
 * result. Every failure maps to an [EntitlementError] without a message.
 */
class EntitlementClient(
    private val http: OkHttpClient,
    baseUrl: String,
    private val tokens: TokenSource,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : EntitlementApi {
    private val base = baseUrl.trim().takeIf { it.isNotEmpty() }?.toHttpUrlOrNull()

    override suspend fun verify(purchaseToken: String, productId: String): EntitlementResult {
        val body = buildJsonObject {
            put("purchase_token", purchaseToken)
            put("product_id", productId)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        return call(VERIFY_PATH) { post(body) }
    }

    override suspend fun current(): EntitlementResult = call(CURRENT_PATH) { get() }

    private suspend fun call(path: String, method: Request.Builder.() -> Request.Builder): EntitlementResult {
        val url = base?.resolve(path) ?: return EntitlementResult.Err(EntitlementError.NOT_CONFIGURED)
        val headers = when (val state = tokens.headers()) {
            is TokenState.Ready -> AuthHeaders.filter(state.headers)
            TokenState.NotConfigured -> return EntitlementResult.Err(EntitlementError.NOT_CONFIGURED)
            is TokenState.Failed -> return EntitlementResult.Err(
                if (state.retryable) EntitlementError.OFFLINE else EntitlementError.UNAVAILABLE,
            )
        }
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .method()
            .build()
        return withContext(io) {
            try {
                http.newCall(request).execute().use(::parse)
            } catch (e: IOException) {
                EntitlementResult.Err(EntitlementError.OFFLINE)
            } catch (e: RuntimeException) {
                EntitlementResult.Err(EntitlementError.UNAVAILABLE)
            }
        }
    }

    private fun parse(response: Response): EntitlementResult {
        val text = runCatching { response.body?.string() }.getOrNull()
        val json = text?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
        if (!response.isSuccessful) {
            val code = (json?.get("error") as? JsonPrimitive)?.contentOrNull
            return EntitlementResult.Err(
                if (response.code == 400 && code == "invalid_token") {
                    EntitlementError.INVALID_TOKEN
                } else {
                    EntitlementError.UNAVAILABLE
                },
            )
        }
        val entitlement = json?.let(::entitlementOf) ?: return EntitlementResult.Err(EntitlementError.UNAVAILABLE)
        return EntitlementResult.Ok(entitlement)
    }

    private fun entitlementOf(json: JsonObject): Entitlement? {
        val proActive = (json["pro_active"] as? JsonPrimitive)?.booleanOrNull ?: return null
        val expiresAt = timeOf(json["expires_at"]) ?: return null
        val signature = (json["signature"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        val state = (json["state"] as? JsonPrimitive)?.contentOrNull?.let(SubState::fromWire)
            ?: if (proActive) SubState.ACTIVE else SubState.NONE
        return Entitlement(proActive, state, expiresAt, timeOf(json["issued_at"]) ?: 0L, signature)
    }

    /** Epoch ms as a number, or an ISO-8601 instant string. */
    private fun timeOf(element: Any?): Long? {
        val p = element as? JsonPrimitive ?: return null
        if (!p.isString) return p.longOrNull
        return runCatching { Instant.parse(p.content).toEpochMilli() }.getOrNull()
    }

    companion object {
        const val VERIFY_PATH = "v1/entitlement/verify"
        const val CURRENT_PATH = "v1/entitlement"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
