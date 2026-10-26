/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.ai

import android.content.Context
import com.manjul.aikeyboard.BuildConfig
import com.manjul.aikeyboard.core.ai.AiClient
import com.manjul.aikeyboard.core.ai.AiEngine
import com.manjul.aikeyboard.core.ai.AiEvent
import com.manjul.aikeyboard.core.ai.AiException
import com.manjul.aikeyboard.core.ai.AiLru
import com.manjul.aikeyboard.core.ai.AiPauseGate
import com.manjul.aikeyboard.core.ai.AiRequest
import com.manjul.aikeyboard.core.ai.AuthHeaders
import com.manjul.aikeyboard.core.ai.CloudAiEngine
import com.manjul.aikeyboard.core.ai.TokenSource
import com.manjul.aikeyboard.core.config.DefaultsRemoteConfig
import com.manjul.aikeyboard.core.config.RemoteConfig
import com.manjul.aikeyboard.core.firebase.DevTokenSource
import com.manjul.aikeyboard.core.firebase.FirebaseBootstrap
import com.manjul.aikeyboard.core.firebase.FirebaseConfig
import com.manjul.aikeyboard.core.firebase.FirebaseTokenSource
import com.manjul.aikeyboard.core.network.HttpClientFactory
import com.manjul.aikeyboard.ime.aiui.ReportPayload
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The app-wide AI pieces (TRD §4.4, §5.2): remote config, kb-api tokens, the cloud engine, the field-scoped LRU, the
 * pause gate and the report sender. One per process (`FlorisApplication.aiAppGraph`), created lazily by the IME.
 *
 * Process start (TRD §2.3): building the graph creates no HTTP client and does not touch Firebase. The client is
 * built on the first request; Firebase starts on the first token fetch (`FirebaseBootstrap`).
 *
 * [isConfigured] is false without a kb-api base URL, or without Firebase options in a build that does not use dev
 * auth: the ✦ chip is then hidden, so a build without a backend never shows a broken feature. The device only ever
 * talks to our kb-api; no AI vendor host or key is in the app.
 */
class AiAppGraph(
    val config: RemoteConfig,
    val tokens: TokenSource,
    val engine: AiEngine,
    val lru: AiLru,
    val pause: AiPauseGate,
    val report: suspend (ReportPayload) -> Unit,
    val isConfigured: Boolean,
) {
    companion object {
        fun create(context: Context): AiAppGraph {
            val baseUrl = BuildConfig.AI_BASE_URL
            val firebase = FirebaseConfig(
                projectId = BuildConfig.FIREBASE_PROJECT_ID,
                appId = BuildConfig.FIREBASE_APP_ID,
                apiKey = BuildConfig.FIREBASE_API_KEY,
                projectNumber = BuildConfig.FIREBASE_PROJECT_NUMBER,
            )
            val tokens: TokenSource = if (BuildConfig.AI_DEV_AUTH) {
                DevTokenSource()
            } else {
                FirebaseTokenSource(FirebaseBootstrap(context, firebase), CoroutineScope(SupervisorJob() + Dispatchers.IO))
            }
            val config = DefaultsRemoteConfig()
            // AiClient adds the auth headers itself, so the client has no auth interceptor.
            val http = lazy { HttpClientFactory.create() }
            val engine = LazyCloudEngine { CloudAiEngine(AiClient(http.value, baseUrl, tokens, config, System::currentTimeMillis)) }
            val reports = AiReportSender(http::value, baseUrl, tokens)
            return AiAppGraph(
                config = config,
                tokens = tokens,
                engine = engine,
                lru = AiLru(),
                pause = AiPauseGate(System::currentTimeMillis),
                report = reports::send,
                isConfigured = isConfigured(baseUrl, BuildConfig.AI_DEV_AUTH, firebase),
            )
        }

        internal fun isConfigured(baseUrl: String, devAuth: Boolean, firebase: FirebaseConfig): Boolean =
            baseUrl.trim().toHttpUrlOrNull() != null && (devAuth || firebase.isConfigured)
    }
}

/** The cloud engine, built on its first request. */
private class LazyCloudEngine(create: () -> AiEngine) : AiEngine {
    private val delegate by lazy(create)

    override val name: String = CloudAiEngine.NAME

    override fun stream(req: AiRequest): Flow<AiEvent> = delegate.stream(req)
}

/**
 * `POST /v1/ai/report` (TRD §11.5): reason, prompt id and request id; the output text only when the user ticked
 * "include the text". Best effort: any failure is dropped, and nothing about the request or response is logged.
 */
internal class AiReportSender(
    private val http: () -> OkHttpClient,
    baseUrl: String,
    private val tokens: TokenSource,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val url = baseUrl.trim().toHttpUrlOrNull()?.resolve(REPORT_PATH)

    suspend fun send(payload: ReportPayload) {
        val target = url ?: return
        val headers = try {
            AuthHeaders.resolve(tokens.headers())
        } catch (_: AiException) {
            return
        }
        val body = bodyOf(payload).toRequestBody(JSON)
        val request = Request.Builder().url(target).post(body).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        withContext(io) {
            try {
                http().newCall(request).execute().close()
            } catch (_: IOException) {
                // Dropped: a report is best effort.
            }
        }
    }

    companion object {
        const val REPORT_PATH = "v1/ai/report"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun bodyOf(payload: ReportPayload): String = buildJsonObject {
            put("client_req_id", payload.clientReqId)
            put("prompt_id", payload.promptId)
            put("reason", payload.reason.wire)
            put("include_text", payload.text != null)
            payload.text?.let { put("text", it) }
        }.toString()
    }
}
