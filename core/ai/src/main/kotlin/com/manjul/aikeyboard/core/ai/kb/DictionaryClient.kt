/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ai.kb

import com.manjul.aikeyboard.core.ai.AuthHeaders
import com.manjul.aikeyboard.core.ai.TokenSource
import com.manjul.aikeyboard.core.ai.TokenState
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** One sense from kb-api `/v1/dictionary`. [toString] prints the part of speech only: the text is user content. */
data class RemoteEntry(
    val headword: String,
    val pos: String,
    val definition: String,
    val example: String?,
    val synonyms: List<String>,
) {
    override fun toString(): String = "RemoteEntry(pos=$pos)"
}

/** The answer of [DictionaryClient.lookup]. */
sealed interface DictionaryResult {
    /** [licenceUrl] is the licence of the entry text, shown as attribution. */
    data class Found(val entries: List<RemoteEntry>, val licenceUrl: String) : DictionaryResult

    data object NotFound : DictionaryResult

    /** 503, any other error status, a network failure, a timeout, a bad body or no token. */
    data object Unavailable : DictionaryResult
}

/**
 * `POST /v1/dictionary` `{word, lang}` on kb-api (Sprint 5, OD-11). kb-api proxies the dictionary source; the
 * device never calls it. 200 is [DictionaryResult.Found], 404 is [DictionaryResult.NotFound], and everything else
 * is [DictionaryResult.Unavailable]. A blank word is [DictionaryResult.NotFound] without a request.
 *
 * This client has no consent gate: the caller decides when a lookup may be sent. The word, the entries and the
 * auth headers never reach a log, an exception or a result's [toString]. Cancelling the caller interrupts the
 * blocking call.
 */
class DictionaryClient(
    private val http: OkHttpClient,
    baseUrl: String,
    private val tokens: TokenSource,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val base = baseUrl.trim().takeIf { it.isNotEmpty() }?.toHttpUrlOrNull()

    suspend fun lookup(word: String, lang: String): DictionaryResult {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return DictionaryResult.NotFound
        val url = base?.resolve(LOOKUP_PATH) ?: return DictionaryResult.Unavailable
        val headers = when (val state = tokens.headers()) {
            is TokenState.Ready -> AuthHeaders.filter(state.headers)
            TokenState.NotConfigured, is TokenState.Failed -> return DictionaryResult.Unavailable
        }
        val body = buildJsonObject {
            put("word", trimmed)
            put("lang", lang)
        }.toString().toRequestBody(JSON)
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
                DictionaryResult.Unavailable
            } catch (e: RuntimeException) {
                DictionaryResult.Unavailable
            }
        }
    }

    private fun parse(response: Response): DictionaryResult {
        if (response.code == 404) return DictionaryResult.NotFound
        if (response.code != 200) return DictionaryResult.Unavailable
        val text = runCatching { response.body?.string() }.getOrNull() ?: return DictionaryResult.Unavailable
        val json = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: return DictionaryResult.Unavailable
        val licenceUrl = json.str("licence_url") ?: return DictionaryResult.Unavailable
        val raw = json["entries"] as? JsonArray ?: return DictionaryResult.Unavailable
        val entries = raw.map { element ->
            val e = element as? JsonObject ?: return DictionaryResult.Unavailable
            RemoteEntry(
                headword = e.str("headword") ?: return DictionaryResult.Unavailable,
                pos = e.str("pos") ?: return DictionaryResult.Unavailable,
                definition = e.str("definition") ?: return DictionaryResult.Unavailable,
                example = e.str("example"),
                synonyms = (e["synonyms"] as? JsonArray).orEmpty().mapNotNull { s ->
                    (s as? JsonPrimitive)?.takeIf { it.isString }?.content
                },
            )
        }
        if (entries.isEmpty()) return DictionaryResult.Unavailable
        return DictionaryResult.Found(entries, licenceUrl)
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    companion object {
        const val LOOKUP_PATH = "v1/dictionary"

        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
