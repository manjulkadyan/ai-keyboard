/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translit

import com.manjul.aikeyboard.core.suggest.SuggestDictionary
import com.manjul.aikeyboard.core.translit.Source

/**
 * Keeps a common English word ("phone", "meeting") as Latin instead of turning it into a native word on space. The
 * Roman word is user content: nothing here logs it.
 *
 * @param english the shipped `en.sdict` if it is already loaded, else null (then nothing is kept as Latin). It must
 * never load or block: `keepsLatin` runs on the space path.
 * @param nativeFrequency the lexicon frequency (1..255) of a native candidate, or null when unknown.
 */
class EnglishCheck(
    private val english: () -> SuggestDictionary?,
    private val threshold: Int = 160,
    private val nativeFrequency: (TranslitCandidate) -> Int? = { null },
) {
    /** True when the Roman word is a common English word that no lexicon or mapping candidate beats. */
    fun keepsLatin(roman: String, top: TranslitCandidate): Boolean {
        if (top.source != Source.LEXICON) return false // A mapping always wins; rule-only output has no claim.
        if (roman.length <= SHORT_WORD && roman.lowercase() !in SHORT_ALLOW) return false
        val en = english()?.frequency(roman) ?: 0
        if (en < threshold) return false
        return (nativeFrequency(top) ?: 0) < en
    }

    private companion object {
        const val SHORT_WORD = 2
        val SHORT_ALLOW = setOf("ok", "hi", "no", "so", "to", "is")
    }
}
