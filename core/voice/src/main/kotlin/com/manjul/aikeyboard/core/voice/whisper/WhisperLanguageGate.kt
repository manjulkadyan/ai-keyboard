/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.voice.whisper

/**
 * Subtype languages that may dictate with Whisper. Built from a build config list: "*" enables every
 * language (debug and beta), "" none (release until accuracy is checked per language), otherwise
 * comma-separated language subtags such as "en,hi".
 */
class WhisperLanguageGate private constructor(private val languages: Set<String>?) {
    val anyEnabled: Boolean get() = languages == null || languages.isNotEmpty()

    fun enabled(lang: String): Boolean =
        languages == null || WhisperSpeechEngine.whisperLanguage(lang) in languages

    companion object {
        fun parse(list: String): WhisperLanguageGate {
            if (list.trim() == "*") return WhisperLanguageGate(null)
            return WhisperLanguageGate(list.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet())
        }
    }
}
