/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ai

import java.io.IOException

/**
 * Every way an AI call can fail, as the client UI needs it (TRD §4.4.5, Sprint 3 decision 16). None of them
 * carries user text or a raw server or exception message.
 */
sealed interface AiError {
    /** No connection, or the connection dropped. "AI needs internet". */
    data object Offline : AiError

    /** No text for `ai_timeout_ms`, before the first delta or between two. "Taking too long" + Retry. */
    data object Timeout : AiError

    /** 402: the daily or monthly quota is used up until [resetsAtEpochMs]. */
    data class Quota(val resetsAtEpochMs: Long) : AiError

    /** 429, or 503 `overloaded`: try again in [retryAfterS] seconds. */
    data class RateLimited(val retryAfterS: Long) : AiError

    /** 451, or a stream `error: refused`. "Couldn't rewrite this text". */
    data object Refused : AiError

    /** 503 `ai_paused`: the server kill switch or spend breaker. The chip hides for an hour ([AiPauseGate]). */
    data object Paused : AiError

    /** Over the client cap (2,000 chars) or 400 `too_long`. "Text is too long". */
    data object TooLong : AiError

    /** 401/403, other 4xx/5xx, a malformed response or failed tokens. "AI is unavailable right now". */
    data object Unavailable : AiError

    /** Firebase (or dev auth) is not configured in this build: no request is ever sent. */
    data object NotConfigured : AiError
}

/**
 * An [AiError] as an exception. It is an [IOException] so OkHttp interceptors may throw it. Its message is the
 * error's class name only, it has no cause and records no stack trace, so no request text or server message
 * can travel with it.
 */
class AiException(val error: AiError) : IOException(error::class.java.simpleName) {
    override fun fillInStackTrace(): Throwable = this
}
