/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.voice

data class VoiceTimings(
    val silenceMs: Long = 2000,
    val restartDelayMs: Long = 250,
    val maxRestarts: Int = 3,
    val busyDelayMs: Long = 400,
    val maxBusyRetries: Int = 2,
    val sessionCapMs: Long = 60_000,
)
