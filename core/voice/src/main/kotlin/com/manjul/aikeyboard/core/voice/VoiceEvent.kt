/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.voice

/** What the IME should do with the host text field. The text is user content: never log it. */
sealed interface VoiceOutput {
    data class Composing(val text: String) : VoiceOutput
    data class Commit(val text: String) : VoiceOutput
    data object ClearComposing : VoiceOutput
}
