/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.network

import com.manjul.aikeyboard.core.ai.TokenSource
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * Builds the app's kb-api OkHttpClients. No logging interceptor (bodies are user content), no extra headers
 * beyond OkHttp's default User-Agent, no retry on connection failure (a POST is retried by the caller with the
 * same `client_req_id`). The read timeout is a backstop only: AiClient runs its own 10 s idle timer.
 */
object HttpClientFactory {
    const val CONNECT_TIMEOUT_S = 10L
    const val READ_TIMEOUT_S = 30L
    const val WRITE_TIMEOUT_S = 10L

    /** Without [tokens] the client adds no auth headers (use it for AiClient, which adds them itself). */
    fun create(tokens: TokenSource? = null): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_S, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .apply { if (tokens != null) addInterceptor(AuthHeadersInterceptor(tokens)) }
        .build()
}
