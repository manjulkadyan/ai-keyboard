/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.tools.dictbuild

/**
 * English leakage in a non-English word list (PRD FR-05 AC2). Subtitle corpora carry a lot of English; an entry
 * counts as English when it appears in the en list and its en frequency per million is more than 2x its
 * frequency per million in the list.
 */
object EnglishLeak {
    /** Fraction of entries in [list] that count as English against [en]; 0 for an empty list. */
    fun share(list: Map<String, Int>, en: Map<String, Int>): Double {
        if (list.isEmpty()) return 0.0
        val rates = Rates(list, en)
        return list.keys.count { rates.isEnglish(it) }.toDouble() / list.size
    }

    fun isEnglish(word: String, list: Map<String, Int>, en: Map<String, Int>): Boolean = Rates(list, en).isEnglish(word)

    /**
     * [list] with English entries removed until their [share] is at most [maxShare]. The most English entries go
     * first (highest en rate / list rate), so borderline native words such as fr "place" or pt "me", which are
     * common in both languages, are the last to go. Repeats until stable, since removing words raises the others'
     * rates. `maxShare = 0` removes every English entry.
     */
    fun removeLeaks(list: Map<String, Int>, en: Map<String, Int>, maxShare: Double = 0.0): Map<String, Int> {
        require(maxShare in 0.0..<1.0)
        var current = list
        while (current.isNotEmpty()) {
            val rates = Rates(current, en)
            val leaks = current.keys.filter { rates.isEnglish(it) }
            if (leaks.size <= maxShare * current.size) return current
            // Remove enough that the share would reach maxShare if no new leak appeared.
            val needed = Math.ceil((leaks.size - maxShare * current.size) / (1.0 - maxShare)).toInt().coerceAtLeast(1)
            val drop = leaks.sortedByDescending { rates.ratio(it) }.take(needed).toHashSet()
            current = current.filterKeys { it !in drop }
        }
        return current
    }

    private class Rates(private val list: Map<String, Int>, private val en: Map<String, Int>) {
        private val listTotal = list.values.sumOf { it.toLong() }.toDouble()
        private val enTotal = en.values.sumOf { it.toLong() }.toDouble()

        fun isEnglish(word: String): Boolean {
            val enCount = en[word] ?: return false
            if (enTotal <= 0.0) return false
            return enCount * 1e6 / enTotal > 2.0 * listPerMillion(word)
        }

        /** en rate over list rate; infinite when the word is absent from the list. */
        fun ratio(word: String): Double {
            val enPerMillion = (en[word] ?: 0) * 1e6 / enTotal
            val listPerMillion = listPerMillion(word)
            return if (listPerMillion <= 0.0) Double.POSITIVE_INFINITY else enPerMillion / listPerMillion
        }

        private fun listPerMillion(word: String): Double =
            if (listTotal <= 0.0) 0.0 else (list[word] ?: 0) * 1e6 / listTotal
    }
}
