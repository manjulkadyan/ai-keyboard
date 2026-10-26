/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.suggest

import kotlin.math.min

/**
 * Ranks dictionary and learned words for the word being typed (PRD FR-05).
 *
 * Score, in [0, 1]: a prior of `0.5 + 0.5·frequency + 0.25·bigram + 0.5·learned` (each term normalised to 0..1;
 * bigram is the larger of the dictionary and learned bigram), multiplied by `1 − 0.2·distance` where distance is
 * the weighted Damerau-Levenshtein distance with [KeyProximity] substitution costs. Prefix completions take a
 * further ×0.85, so an exact-prefix completion outranks a one-edit correction of equal prior. With fewer than 2
 * typed characters corrections are listed before completions. The unclamped value breaks ties between words that
 * both clamp to 1.0.
 *
 * Search bound: distance 0 for 1 character, 1 for 2–3 characters, 2 from 4; completions only from exact
 * prefixes below 4 characters and within distance 1 from 4. From 4 characters the first letter is anchored (see
 * [SuggestDictionary.search]).
 *
 * The typed word comes first when it is a dictionary or learned word. Blocked words never appear. Output case
 * follows the typed word (Capitalised, ALL CAPS). Typed words are user content: nothing here logs.
 */
class SuggestEngine(
    private val dict: SuggestDictionary,
    private val proximity: KeyProximity,
    private val learned: LearnedWords,
) {
    fun suggest(typed: String, previousWord: String?, max: Int = 3): List<Candidate> {
        if (max <= 0 || typed.isEmpty() || typed.any { it.isWhitespace() }) return emptyList()
        val locale = dict.locale
        val lower = typed.lowercase()
        val n = lower.length
        val prev = previousWord?.trim()?.takeIf { it.isNotEmpty() }?.lowercase()
        val maxDistance = when {
            n <= 1 -> 0.0
            n <= 3 -> 1.0
            else -> 2.0
        }
        val maxCompletionDistance = if (n >= 4) 1.0 else 0.0

        val best = HashMap<String, Ranked>()
        for (m in dict.search(lower, maxDistance, maxCompletionDistance, proximity, COMPLETION_LIMIT)) {
            val word = dict.wordAt(m.index)
            val key = word.lowercase()
            if (key == lower || learned.isBlocked(locale, key)) continue
            val ranked = rank(word, key, dict.frequencyAt(m.index), prev, m.distance, m.completion, typedWord = false)
            val existing = best[key]
            if (existing == null || ranked.raw > existing.raw) best[key] = ranked
        }

        val result = ArrayList<Candidate>(min(max, best.size + 1))
        if (!learned.isBlocked(locale, lower) && (dict.containsExact(typed) || learned.boost(locale, lower) > 0)) {
            val ranked = rank(typed, lower, dict.frequency(typed), prev, 0.0, completion = false, typedWord = true)
            result += Candidate(typed, ranked.score, isExactOrLearned = true, isTypedWord = true)
        }

        val ordered = best.values.sortedWith(
            compareBy<Ranked> { if (n < 2 && it.completion) 1 else 0 }
                .thenByDescending { it.raw }
                .thenByDescending { it.frequency }
                .thenBy { it.word },
        )
        for (r in ordered) {
            if (result.size >= max) break
            result += Candidate(applyCase(r.word, typed), r.score, isExactOrLearned = r.learned, isTypedWord = false)
        }
        return if (result.size > max) result.subList(0, max) else result
    }

    /**
     * Whether [typed] is a real word here: in the dictionary or learned. Unlike [suggest] this ignores blocking, so a
     * word the user removed from suggestions still counts as spelled correctly (never autocorrected).
     */
    fun isKnown(typed: String): Boolean {
        if (typed.isEmpty()) return false
        val lower = typed.lowercase()
        return dict.containsExact(typed) || dict.containsExact(lower) || learned.boost(dict.locale, lower) > 0
    }

    private fun rank(
        word: String,
        key: String,
        frequency: Int,
        prev: String?,
        distance: Double,
        completion: Boolean,
        typedWord: Boolean,
    ): Ranked {
        val boost = learned.boost(dict.locale, key)
        val bigram = if (prev == null) {
            0.0
        } else {
            maxOf(dict.bigram(prev, key) / 255.0, saturate(learned.bigram(dict.locale, prev, key)))
        }
        val prior = 0.5 + 0.5 * (frequency / 255.0) + 0.25 * bigram + 0.5 * saturate(boost)
        var factor = 1.0 - 0.2 * distance
        if (completion) factor *= COMPLETION_FACTOR
        val raw = prior * factor.coerceAtLeast(0.0)
        return Ranked(word, raw, raw.coerceIn(0.0, 1.0), frequency, completion, boost > 0 || typedWord)
    }

    private class Ranked(
        val word: String,
        val raw: Double,
        val score: Double,
        val frequency: Int,
        val completion: Boolean,
        val learned: Boolean,
    )

    private companion object {
        const val COMPLETION_LIMIT = 12
        const val COMPLETION_FACTOR = 0.85
        const val LEARNED_SATURATION = 5.0

        fun saturate(count: Int): Double = min(1.0, count.coerceAtLeast(0) / LEARNED_SATURATION)
    }
}

/** True for text with at least one cased letter and no lower-case letters ("TEH", "USA"). */
internal fun isAllCaps(text: String): Boolean = text.any { it.isUpperCase() } && text.none { it.isLowerCase() }

/** Casing of [word] made to follow [typed]: ALL CAPS when typed is ALL CAPS (> 1 char), Capitalised when typed is. */
internal fun applyCase(word: String, typed: String): String = when {
    typed.length > 1 && isAllCaps(typed) -> word.uppercase()
    typed.firstOrNull()?.isUpperCase() == true -> word.replaceFirstChar { it.titlecase() }
    else -> word
}
