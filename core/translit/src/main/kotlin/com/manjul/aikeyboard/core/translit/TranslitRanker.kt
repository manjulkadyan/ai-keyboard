/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translit

/**
 * Scores decoder output and merges it with the user mapping (spec "TranslitRanker"):
 * `-(ruleWeight * ruleCost) + lexiconBonus + frequency / 255 + mappingBonus + boost`. The lexicon terms apply only to
 * a known word, the mapping bonus only to the mapping. A mapping is always first. Duplicates keep the best score.
 */
class TranslitRanker(private val lang: TranslitLang, private val lexicon: TranslitLexicon) {
    private val ruleWeight: Double = when (lang) {
        TranslitLang.HI -> HI_RULE_WEIGHT
        // Every ur.sdict frequency is 16, so rule cost has to separate known words (spec "Urdu frequency weakness").
        TranslitLang.UR -> UR_RULE_WEIGHT
    }

    fun rank(hypotheses: List<Hypothesis>, mapping: String?, boost: (String) -> Int): List<Candidate> {
        val bestCost = HashMap<String, Double>(hypotheses.size * 2)
        for (h in hypotheses) {
            val old = bestCost[h.native]
            if (old == null || h.cost < old) bestCost[h.native] = h.cost
        }
        val out = ArrayList<Candidate>(bestCost.size + 1)
        if (mapping != null) {
            val ruleScore = bestCost.remove(mapping)?.let { -(ruleWeight * it) } ?: 0.0
            val score = ruleScore + lexiconScore(mapping) + MAPPING_BONUS + boost(mapping)
            out += Candidate(mapping, score, Source.MAPPING)
        }
        for ((native, cost) in bestCost) {
            val source = if (lexicon.frequency(native) != null) Source.LEXICON else Source.RULES
            out += Candidate(native, -(ruleWeight * cost) + lexiconScore(native) + boost(native), source)
        }
        out.sortWith(compareBy<Candidate> { it.source != Source.MAPPING }.thenByDescending { it.score })
        return out
    }

    private fun lexiconScore(native: String): Double {
        val f = lexicon.frequency(native) ?: return 0.0
        return LEXICON_BONUS + f / 255.0
    }

    override fun toString(): String = "TranslitRanker(lang=${lang.tag})"

    companion object {
        // Starting values. Task 6 tunes them against the smoke sets.
        const val HI_RULE_WEIGHT: Double = 1.0
        const val UR_RULE_WEIGHT: Double = 1.6
        const val LEXICON_BONUS: Double = 2.0
        const val MAPPING_BONUS: Double = 10.0
    }
}
