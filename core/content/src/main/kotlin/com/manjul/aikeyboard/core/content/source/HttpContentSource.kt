/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.source

import com.manjul.aikeyboard.core.content.ContentPaths
import com.manjul.aikeyboard.core.network.HttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Reads content files from the CDN at [baseUrl] (`aikb.content.baseUrl`). Requests carry no user identifier
 * (FR-23 AC4): no auth header or App Check token (the client has no `AuthHeadersInterceptor`, and a network
 * interceptor strips them anyway), no cookie (no cookie jar), no query string, and the fixed User-Agent [USER_AGENT].
 */
class HttpContentSource(
    baseUrl: String,
    client: OkHttpClient = HttpClientFactory.create(tokens = null),
) : ContentSource {
    private val base: HttpUrl = baseUrl.toHttpUrl().also {
        require(it.query == null && it.fragment == null) { "the content base URL must not have a query or fragment" }
    }.let { if (it.encodedPath.endsWith("/")) it else it.newBuilder().addPathSegment("").build() }

    private val client: OkHttpClient = client.newBuilder()
        .cookieJar(CookieJar.NO_COOKIES)
        .addNetworkInterceptor { chain ->
            val request = chain.request().newBuilder()
                .removeHeader("Authorization")
                .removeHeader("X-Firebase-AppCheck")
                .removeHeader("Cookie")
                .header("User-Agent", USER_AGENT)
                .build()
            chain.proceed(request)
        }
        .build()

    override suspend fun fetch(path: String): ByteArray {
        if (!ContentPaths.isSafePath(path)) throw ContentFetchException("unsafe path")
        val url = base.newBuilder().addPathSegments(path).build()
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                    if (!response.isSuccessful) throw ContentFetchException("HTTP ${response.code}")
                    response.body?.bytes() ?: throw ContentFetchException("empty body")
                }
            } catch (e: IOException) {
                // The IOException message can name the host; only its type is kept.
                throw ContentFetchException("network: ${e.javaClass.simpleName}")
            }
        }
    }

    companion object {
        const val USER_AGENT = "AIKeyboard-Content/1"
    }
}
