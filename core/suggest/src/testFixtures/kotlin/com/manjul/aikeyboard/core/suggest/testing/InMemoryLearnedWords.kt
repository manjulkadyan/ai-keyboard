/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.suggest.testing

import com.manjul.aikeyboard.core.suggest.LearnedWords

/** Map-backed [LearnedWords] for tests. Keys are lowercase, as [LearnedWords] documents. */
class InMemoryLearnedWords : LearnedWords {
    private val boosts = HashMap<Pair<String, String>, Int>()
    private val bigrams = HashMap<Triple<String, String, String>, Int>()
    private val blocked = HashSet<Pair<String, String>>()

    fun learn(locale: String, word: String, count: Int = 1) {
        val key = locale to word.lowercase()
        boosts[key] = (boosts[key] ?: 0) + count
    }

    fun learnBigram(locale: String, prev: String, word: String, count: Int = 1) {
        val key = Triple(locale, prev.lowercase(), word.lowercase())
        bigrams[key] = (bigrams[key] ?: 0) + count
    }

    fun block(locale: String, word: String) {
        blocked += locale to word.lowercase()
    }

    override fun boost(locale: String, word: String): Int = boosts[locale to word] ?: 0

    override fun bigram(locale: String, prev: String, word: String): Int = bigrams[Triple(locale, prev, word)] ?: 0

    override fun isBlocked(locale: String, word: String): Boolean = (locale to word) in blocked
}
