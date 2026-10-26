/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.network

import com.manjul.aikeyboard.core.ai.AuthHeaders
import com.manjul.aikeyboard.core.ai.TokenSource
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the kb-api auth headers (TRD §5.2) from [tokens]: `X-Firebase-AppCheck` + `Authorization`, or `X-Dev-Uid`
 * in local dev builds. Only [AuthHeaders.ALLOWED] names are added, so no install or device id can be sent
 * (Sprint 3 decision 7). `NotConfigured` and failed tokens throw [com.manjul.aikeyboard.core.ai.AiException]
 * (an IOException) before anything reaches the network.
 *
 * Runs on OkHttp's calling thread, which is never the main thread for kb-api calls; the token fetch blocks it.
 */
class AuthHeadersInterceptor(private val tokens: TokenSource) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val headers = AuthHeaders.resolve(runBlocking { tokens.headers() })
        val request = chain.request().newBuilder()
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()
        return chain.proceed(request)
    }
}
