/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.analytics

/** Lets one `ime_session_start` through per 10 minutes (PRD §12). */
class SessionSampler(private val clock: () -> Long) {
    private var last: Long? = null

    @Synchronized
    fun shouldLogImeSession(): Boolean {
        val now = clock()
        val prev = last
        if (prev != null && now - prev < INTERVAL_MS) return false
        last = now
        return true
    }

    private companion object {
        const val INTERVAL_MS = 10 * 60_000L
    }
}
