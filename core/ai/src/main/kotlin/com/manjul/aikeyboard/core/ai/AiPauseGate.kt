/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ai

/**
 * In-memory AI pause (Sprint 3 decision 16): after 503 `ai_paused` the AI chip is hidden for an hour. Lost on
 * process death on purpose; the server keeps refusing while it is paused. Thread-safe.
 */
class AiPauseGate(private val clock: () -> Long) {
    @Volatile private var pausedUntil = Long.MIN_VALUE

    val isPaused: Boolean
        get() = clock() < pausedUntil

    /** Pauses for [ms] from now; never shortens a pause that already lasts longer. */
    @Synchronized
    fun pauseFor(ms: Long) {
        val until = clock() + ms
        if (until > pausedUntil) pausedUntil = until
    }

    /** Applies [error]: [AiError.Paused] pauses for [PAUSED_MS]; other errors change nothing. */
    fun onError(error: AiError) {
        if (error == AiError.Paused) pauseFor(PAUSED_MS)
    }

    @Synchronized
    fun clear() {
        pausedUntil = Long.MIN_VALUE
    }

    companion object {
        const val PAUSED_MS: Long = 60 * 60 * 1000L
    }
}
