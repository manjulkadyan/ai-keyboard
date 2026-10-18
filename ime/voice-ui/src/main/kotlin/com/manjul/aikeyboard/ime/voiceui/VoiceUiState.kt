/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.voiceui

enum class MessageKind { NO_SERVICE, LANG_UNSUPPORTED, OFFLINE_NO_MODEL, NETWORK, MIC_BUSY, NO_PERMISSION, ERROR }

/** What the smartbar shows for voice typing. Never carries transcript text. */
sealed interface VoiceUiState {
    data object Hidden : VoiceUiState

    /** "Allow microphone, then tap the mic again"; only a tap on this chip opens the permission screen. */
    data object NeedsPermission : VoiceUiState
    data class Listening(val rms: Float) : VoiceUiState
    data object Transcribing : VoiceUiState
    data class Message(
        val kind: MessageKind,
        val offerSystemDownload: Boolean = false,
        val offerWhisperDownload: Boolean = false,
    ) : VoiceUiState

    /** Repeated silence stopped the session; a tap starts a new one. */
    data object TapToResume : VoiceUiState
}
