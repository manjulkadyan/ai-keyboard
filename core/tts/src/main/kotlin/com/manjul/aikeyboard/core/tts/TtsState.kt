/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.tts

/** What the shared [TtsManager] is doing. Carries no text (PC-10). */
sealed interface TtsState {
    /** Not speaking (also before the first [TtsManager.acquire] and after the last release). */
    data object Idle : TtsState

    /** The engine is binding; a [TtsManager.speak] call now runs once it is ready. */
    data object Initializing : TtsState

    /** Speaking chunk [chunk] (0-based) of [chunkCount]. */
    data class Speaking(val chunk: Int, val chunkCount: Int) : TtsState

    /** The platform has no usable TTS engine (init failed). */
    data object Unavailable : TtsState
}

/** Whether a voice exists for a locale (`TextToSpeech.isLanguageAvailable`). */
enum class TtsAvailability {
    AVAILABLE,

    /** The language is supported but its voice data is not installed: offer [TtsManager.installVoiceIntent]. */
    MISSING_DATA,
    NOT_SUPPORTED,
}
