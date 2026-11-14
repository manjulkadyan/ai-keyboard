/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translit

import com.manjul.aikeyboard.core.suggest.DictionaryFormat
import com.manjul.aikeyboard.core.translit.Source
import com.manjul.aikeyboard.core.translit.TranslitLang
import java.io.File
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnglishCheckTest {
    private val english by lazy {
        DictionaryFormat.read(File("src/main/assets/dict/en.sdict").inputStream().buffered())
    }

    private fun candidate(source: Source = Source.LEXICON) =
        TranslitCandidate("x", 1.0, true, null, "w", TranslitLang.UR, source, false)

    private fun check(nativeFreq: Int? = 100) = EnglishCheck({ english }, nativeFrequency = { nativeFreq })

    @Test fun `common English words keep Latin`() {
        assertTrue(check().keepsLatin("phone", candidate()))
        assertTrue(check().keepsLatin("meeting", candidate()))
        assertTrue(check().keepsLatin("ok", candidate()))
    }

    @Test fun `Roman Urdu words do not`() {
        assertFalse(check().keepsLatin("hai", candidate()))
        assertFalse(check().keepsLatin("kya", candidate()))
        assertFalse(check().keepsLatin("nahi", candidate()))
    }

    @Test fun `a mapping for phone wins`() = assertFalse(check().keepsLatin("phone", candidate(Source.MAPPING)))

    @Test fun `a native word more frequent than the English word wins`() =
        assertFalse(check(nativeFreq = 255).keepsLatin("phone", candidate()))

    @Test fun `a short word outside the allow list is never English`() =
        assertFalse(check().keepsLatin("an", candidate()))

    @Test fun `a missing dictionary keeps nothing`() {
        assertFalse(EnglishCheck({ null }).keepsLatin("phone", candidate()))
    }
}
