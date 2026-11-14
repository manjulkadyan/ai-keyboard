/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translit

import java.io.File

/** Map-backed [TranslitLexicon] for tests. Every prefix of every word (and "") counts as a prefix. */
class MapLexicon(private val words: Map<String, Int>) : TranslitLexicon {
    private val prefixes: Set<String> =
        words.keys.flatMapTo(HashSet()) { w -> (0..w.length).map { w.substring(0, it) } }

    override fun frequency(nativeWord: String): Int? = words[nativeWord]

    override fun prefixExists(nativePrefix: String): Boolean = nativePrefix in prefixes
}

/** The hand-written mini lexicons under `src/test/resources/lexicon` and the shipped dictionaries. */
object TestLexicons {
    fun mini(lang: TranslitLang): MapLexicon {
        val stream = TestLexicons::class.java.getResourceAsStream("/lexicon/${lang.tag}-mini.txt")
            ?: error("missing mini lexicon")
        val words = stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }
                .map { it.split('\t') }
                .associate { it[0] to it[1].toInt() }
        }
        return MapLexicon(words)
    }

    /** `app/src/main/assets/dict`, passed in by the `translit.dictDir` system property. */
    fun dictDir(): File = File(System.getProperty("translit.dictDir") ?: error("translit.dictDir is not set"))

    fun shipped(lang: TranslitLang): SdictLexicon =
        File(dictDir(), "${lang.tag}.sdict").inputStream().use { SdictLexicon.read(it) }
}
