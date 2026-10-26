/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.suggest.testing

import com.manjul.aikeyboard.core.suggest.DictionaryFormat
import com.manjul.aikeyboard.core.suggest.SuggestDictionary
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

/** Small hand-written dictionaries for tests, built through [DictionaryFormat] so every test exercises the format. */
object TestDictionaries {
    val EN_UNIGRAMS: List<Pair<String, Int>> = listOf(
        "the" to 100_000,
        "they" to 8_000,
        "then" to 6_000,
        "hello" to 3_000,
        "help" to 2_500,
        "world" to 2_000,
        "helm" to 3,
        "there" to 5_000,
        "word" to 1_500,
        "say" to 4_000,
    )

    fun en(bigrams: List<Triple<String, String, Int>> = emptyList()): SuggestDictionary =
        build("en", EN_UNIGRAMS, bigrams)

    fun build(
        locale: String,
        unigrams: List<Pair<String, Int>>,
        bigrams: List<Triple<String, String, Int>> = emptyList(),
    ): SuggestDictionary {
        val out = ByteArrayOutputStream()
        DictionaryFormat.write(out, locale, unigrams, bigrams)
        return DictionaryFormat.read(ByteArrayInputStream(out.toByteArray()))
    }

    /** [count] distinct random lowercase words of 2..10 letters with random counts. Deterministic for a seed. */
    fun syntheticUnigrams(count: Int, seed: Long = 42L): List<Pair<String, Int>> {
        val random = Random(seed)
        val words = LinkedHashSet<String>(count * 2)
        while (words.size < count) {
            val length = random.nextInt(2, 11)
            words += buildString(length) { repeat(length) { append('a' + random.nextInt(26)) } }
        }
        return words.map { it to random.nextInt(1, 1_000_000) }
    }

    fun synthetic(count: Int, seed: Long = 42L): SuggestDictionary = build("en", syntheticUnigrams(count, seed))
}
