/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translit

import com.manjul.aikeyboard.core.suggest.DictionaryFormat
import com.manjul.aikeyboard.core.suggest.SuggestDictionary
import java.io.InputStream

/**
 * A [TranslitLexicon] over a shipped `.sdict` word list (`dict/ur.sdict`, `dict/hi.sdict`). [SuggestDictionary]
 * returns frequency 0 for an unknown word; here that is null.
 */
class SdictLexicon(private val dict: SuggestDictionary) : TranslitLexicon {
    override fun frequency(nativeWord: String): Int? = dict.frequency(nativeWord).takeIf { it > 0 }

    override fun prefixExists(nativePrefix: String): Boolean = dict.hasPrefix(nativePrefix)

    override fun toString(): String = "SdictLexicon(size=${dict.size})"

    companion object {
        /** Reads a whole `.sdict` stream. The caller closes [input]. */
        fun read(input: InputStream): SdictLexicon = SdictLexicon(DictionaryFormat.read(input))
    }
}
