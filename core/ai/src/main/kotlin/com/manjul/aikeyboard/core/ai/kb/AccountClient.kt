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
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** The answer of [AccountApi.delete]. `:core:ai` is pure JVM, so this does not use `Outcome`. */
sealed interface AccountResult {
    /** kb-api answered `{deleted: true}`: the uid's server data is gone or queued for deletion. */
    data object Deleted : AccountResult

    data class Err(val error: AiError) : AccountResult
}

/** The account calls of kb-api. [AccountClient] is the real one; tests use a fake. */
interface AccountApi {
    suspend fun delete(clientReqId: String): AccountResult
}

/**
 * `POST /v1/account/delete` on kb-api (Delete my data, TRD §11.6): the body is `{client_req_id}` and the auth
 * headers identify the uid. kb-api answers `{deleted: true}`, or 400, 401, 500 or 503; every one of those, and
 * any other answer, is an error, so the caller wipes nothing.
 *
 * Deleting is a privacy right, so unlike the AI calls this does not need `consentAiAt`: it is sent whenever a token
 * can be had. The auth headers are credentials: they never reach a log, an exception or a result. Every failure
 * maps to an [AiError] without a message. Cancelling the caller interrupts the blocking call.
 */
class AccountClient(
    private val http: OkHttpClient,
    baseUrl: String,
    private val tokens: TokenSource,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : AccountApi {
    private val base = baseUrl.trim().takeIf { it.isNotEmpty() }?.toHttpUrlOrNull()

    override suspend fun delete(clientReqId: String): AccountResult {
        if (clientReqId.isEmpty() || clientReqId.length > MAX_REQ_ID_CHARS) {
            return AccountResult.Err(AiError.Unavailable)
        }
        val url = base?.resolve(DELETE_PATH) ?: return AccountResult.Err(AiError.NotConfigured)
        val headers = when (val state = tokens.headers()) {
            is TokenState.Ready -> AuthHeaders.filter(state.headers)
            TokenState.NotConfigured -> return AccountResult.Err(AiError.NotConfigured)
            is TokenState.Failed -> return AccountResult.Err(
                if (state.retryable) AiError.Offline else AiError.Unavailable,
            )
        }
        val body = buildJsonObject { put("client_req_id", clientReqId) }.toString().toRequestBody(JSON)
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .post(body)
            .build()
        return runInterruptible(io) {
            try {
                http.newCall(request).execute().use(::parse)
            } catch (e: IOException) {
                AccountResult.Err(AiError.Offline)
            } catch (e: RuntimeException) {
                AccountResult.Err(AiError.Unavailable)
            }
        }
    }

    private fun parse(response: Response): AccountResult {
        if (!response.isSuccessful) return AccountResult.Err(AiError.Unavailable)
        val text = runCatching { response.body?.string() }.getOrNull()
        val json = text?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
        val deleted = (json?.get("deleted") as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
        return if (deleted == true) AccountResult.Deleted else AccountResult.Err(AiError.Unavailable)
    }

    companion object {
        const val DELETE_PATH = "v1/account/delete"

        /** kb-api refuses a longer `client_req_id`. */
        const val MAX_REQ_ID_CHARS = 64

        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** A fresh random request id (a UUID, 36 characters). */
        fun newRequestId(): String = UUID.randomUUID().toString()
    }
}
