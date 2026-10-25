/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate.testing

import com.manjul.aikeyboard.core.translate.LanguageDetector

/** [LanguageDetector] answering from a fixed map (text -> candidates); unknown text gets no candidates. */
class FakeLanguageDetector(
    private val map: Map<String, List<Pair<String, Float>>> = emptyMap(),
) : LanguageDetector {
    @Volatile var calls: Int = 0
        private set

    override suspend fun identify(text: String): List<Pair<String, Float>> {
        calls++
        return map[text].orEmpty()
    }
}
