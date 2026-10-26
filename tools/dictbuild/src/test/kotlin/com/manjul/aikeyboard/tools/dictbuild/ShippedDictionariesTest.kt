/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.tools.dictbuild

import com.manjul.aikeyboard.core.suggest.DictionaryFormat
import com.manjul.aikeyboard.core.suggest.SuggestDictionary
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Guards the committed `app/src/main/assets/dict/<lang>.sdict` files (TRD §8, PRD FR-05 AC2, plan decision 2). */
class ShippedDictionariesTest {
    private val assetsDir = File(System.getProperty("dictbuild.assetsDir"))
    private val wordlistsDir = File(System.getProperty("dictbuild.wordlistsDir"))

    private fun read(lang: String): SuggestDictionary =
        File(assetsDir, "$lang.sdict").inputStream().use { DictionaryFormat.read(it) }

    @ParameterizedTest
    @ValueSource(strings = ["en", "es", "fr", "pt", "id", "ar", "hi", "ur"])
    fun `shipped dictionary reads back and is within budget`(lang: String) {
        val file = File(assetsDir, "$lang.sdict")
        assertTrue(file.isFile, "missing $lang.sdict")
        assertTrue(file.length() <= 8L * 1024 * 1024, "$lang.sdict is larger than 8 MB")
        val dict = read(lang)
        assertEquals(lang, dict.locale)
        assertTrue(dict.size >= Main.minWords(lang), "$lang.sdict has ${dict.size} words, want >= ${Main.minWords(lang)}")
        for (w in dict.wordList()) assertTrue(WordFilter.keep(lang, w), "$lang.sdict holds a filtered entry")
    }

    @ParameterizedTest
    @ValueSource(strings = ["es", "fr", "pt", "id", "ar", "hi", "ur"])
    fun `non-english dictionaries leak under 5 percent english`(lang: String) {
        val en = Main.readCounts(File(wordlistsDir, "en"), "en")
        val raw = Main.readCounts(File(wordlistsDir, lang), lang)
        val shipped = read(lang).wordList().associateWith { raw[it] ?: Main.UR_FLAT_COUNT }
        val share = EnglishLeak.share(shipped, en)
        assertTrue(share < 0.05, "$lang English share $share")
    }

    @ParameterizedTest
    @ValueSource(strings = ["en", "es", "fr", "pt", "id", "ar", "hi", "ur"])
    fun `LICENSES md has a row naming the file`(lang: String) {
        val licences = File(assetsDir, "LICENSES.md").readLines()
        assertTrue(licences.any { it.startsWith("|") && it.contains("`dict/$lang.sdict`") }, "no LICENSES.md row for $lang")
    }
}
