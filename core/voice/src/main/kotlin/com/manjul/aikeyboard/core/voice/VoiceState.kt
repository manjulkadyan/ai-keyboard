/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.voice

enum class VoiceErrorKind { NO_PERMISSION, NETWORK, LANGUAGE_UNAVAILABLE, BUSY, NO_ENGINE, OTHER }

sealed interface VoiceState {
    data object Idle : VoiceState
    data object Starting : VoiceState
    data class Listening(val rmsDb: Float) : VoiceState

    /** Whisper only: audio captured, waiting for the transcript. */
    data object Transcribing : VoiceState
    data object Finalizing : VoiceState
    data class Error(val kind: VoiceErrorKind) : VoiceState
}
