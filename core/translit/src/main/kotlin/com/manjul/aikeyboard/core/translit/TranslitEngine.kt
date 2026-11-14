/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translit

/**
 * Roman word to ranked native candidates for one language: the user mapping, then the [BeamDecoder] output scored by
 * the [TranslitRanker]. Pure and synchronous. The Roman word and every candidate are user content: nothing here logs
 * them, and [toString] holds no text.
 */
class TranslitEngine(
    val lang: TranslitLang,
    table: RuleTable,
    lexicon: TranslitLexicon,
    val mappings: CustomMappings,
    beamWidth: Int = 16,
) {
    init {
        require(table.lang == lang) { "Rule table language does not match" }
    }

    private val decoder = BeamDecoder(table, lexicon, beamWidth)
    private val ranker = TranslitRanker(lang, lexicon)

    /** At most [max] candidates, best first. A user mapping for [roman] is always first. */
    fun suggest(roman: String, max: Int, boost: (String) -> Int = { 0 }): List<Candidate> {
        if (max <= 0 || roman.isEmpty()) return emptyList()
        return ranker.rank(decoder.decode(roman), mappings.get(roman), boost).take(max)
    }

    override fun toString(): String = "TranslitEngine(lang=${lang.tag})"
}
