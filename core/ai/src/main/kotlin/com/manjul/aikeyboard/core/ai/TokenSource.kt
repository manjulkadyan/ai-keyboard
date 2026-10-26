/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ai

/** The auth headers for one kb-api request (TRD §5.2). */
sealed interface TokenState {
    /** Send these headers. Only [AuthHeaders.ALLOWED] names are ever sent. */
    data class Ready(val headers: Map<String, String>) : TokenState {
        override fun toString(): String = "Ready(${headers.keys})"
    }

    /** This build has no Firebase options and no dev auth: AI is unavailable and nothing is sent. */
    data object NotConfigured : TokenState

    /** Fetching a token failed; [retryable] when a later attempt may work (for example a network error). */
    data class Failed(val retryable: Boolean) : TokenState
}

/** Supplies the auth headers: App Check + Firebase ID token, or `X-Dev-Uid` in local dev builds. */
interface TokenSource {
    /** Current headers; may fetch or refresh tokens. Never throws. */
    suspend fun headers(): TokenState

    /** Starts fetching tokens in the background so the first AI tap is faster. Never throws. */
    fun prefetch()
}

/** The only headers a [TokenSource] may add. No install or device identifier exists (Sprint 3 decision 7). */
object AuthHeaders {
    const val APP_CHECK = "X-Firebase-AppCheck"
    const val AUTHORIZATION = "Authorization"
    const val DEV_UID = "X-Dev-Uid"

    val ALLOWED: Set<String> = setOf(APP_CHECK, AUTHORIZATION, DEV_UID)

    /** The [ALLOWED] subset of [headers]; anything else is dropped. */
    fun filter(headers: Map<String, String>): Map<String, String> =
        headers.filter { (name, value) -> name in ALLOWED && value.isNotEmpty() }

    /** [state] as headers to send, or the [AiException] to fail with before anything is sent. */
    fun resolve(state: TokenState): Map<String, String> = when (state) {
        is TokenState.Ready -> filter(state.headers)
        TokenState.NotConfigured -> throw AiException(AiError.NotConfigured)
        is TokenState.Failed -> throw AiException(AiError.Unavailable)
    }
}
