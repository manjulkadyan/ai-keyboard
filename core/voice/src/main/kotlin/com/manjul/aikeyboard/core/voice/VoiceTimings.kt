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
    /** Upper bound for a batch transcription after the user stops (ruling R20). */
    val transcribeTimeoutMs: Long = 45_000,
    /** Whisper on 60 s of audio can need longer than [transcribeTimeoutMs] on a slow phone (ruling R33). */
    val whisperTranscribeTimeoutMs: Long = 120_000,
) {
    fun transcribeTimeoutFor(kind: EngineKind): Long =
        if (kind == EngineKind.WHISPER) whisperTranscribeTimeoutMs else transcribeTimeoutMs
}
