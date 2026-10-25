/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate

/**
 * Language identification. The text passed in is user content, never logged.
 */
interface LanguageDetector {
    /** Candidate languages as (BCP-47 tag, confidence 0..1), most likely first. Empty when unsure. */
    suspend fun identify(text: String): List<Pair<String, Float>>
}
