/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.config

/**
 * Typed read access to remote-configurable values. Keys are [ConfigKeys]; reading an unknown key, or a key
 * as the wrong type, throws [IllegalArgumentException] (a programming error, never user data).
 */
interface RemoteConfig {
    fun bool(key: String): Boolean

    fun long(key: String): Long

    /** A [Long] value is also readable as a double. */
    fun double(key: String): Double
}

/** Every remote config key (Sprint 3 decision 5). */
object ConfigKeys {
    /** Global AI kill switch on the client: false hides every AI entry point. */
    const val AI_ENABLED_GLOBAL = "ai_enabled_global"

    /** Minimum top-candidate score for autocorrect on space (PRD FR-05). */
    const val AUTOCORRECT_MIN_SCORE = "autocorrect_min_score"

    /** With no streamed text for this long, the AI sheet shows "Still working…". */
    const val AI_STILL_WORKING_MS = "ai_still_working_ms"

    /** No text delta for this long, before the first one or between two, ends the request with a timeout. */
    const val AI_TIMEOUT_MS = "ai_timeout_ms"
}
