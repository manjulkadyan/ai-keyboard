/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.suggest

import kotlin.math.min

/**
 * An immutable word list for one locale, read by [DictionaryFormat.read]. Words are kept in one sorted array that
 * is walked as an implicit trie (every prefix is a contiguous index range), so a search visits only the branches
 * within the edit-distance bound and never scans the whole list. Frequencies are 0..255 (0 = absent).
 *
 * Lookups try the exact form first, then the lowercase form, so typed "The" finds "the".
 */
class SuggestDictionary internal constructor(
    val locale: String,
    private val words: Array<String>,
    private val frequencies: ByteArray,
    private val bigrams: Map<Long, Int>,
) {
    val size: Int get() = words.size

    private val maxWordLength: Int = words.maxOfOrNull { it.length } ?: 0

    fun frequency(word: String): Int {
        val i = indexOf(word)
        return if (i < 0) 0 else frequencyAt(i)
    }

    fun bigram(prev: String, word: String): Int {
        if (bigrams.isEmpty()) return 0
        val p = indexOf(prev)
        val w = indexOf(word)
        if (p < 0 || w < 0) return 0
        return bigrams[(p.toLong() shl 32) or w.toLong()] ?: 0
    }

    fun containsExact(word: String): Boolean = indexOf(word) >= 0

    /** Words within [maxDistance] (uniform costs) of [prefix], as whole words or as completions of it. */
    internal fun candidatesNear(prefix: String, maxDistance: Int): Sequence<String> =
        search(prefix, maxDistance.toDouble(), maxDistance.toDouble(), SubstitutionCost.UNIFORM, Int.MAX_VALUE)
            .asSequence().map { words[it.index] }.distinct()

    internal fun wordAt(index: Int): String = words[index]

    internal fun frequencyAt(index: Int): Int = frequencies[index].toInt() and 0xFF

    internal fun indexOf(word: String): Int {
        if (word.isEmpty()) return -1
        val exact = words.binarySearch(word)
        if (exact >= 0) return exact
        val lower = word.lowercase()
        if (lower == word) return -1
        val i = words.binarySearch(lower)
        return if (i >= 0) i else -1
    }

    /**
     * A word found by [search]. [completion] means [typed] matched a prefix of the word within [distance];
     * otherwise [distance] is the whole-word distance.
     */
    internal class Match(val index: Int, val distance: Double, val completion: Boolean)

    /**
     * Bounded OSA search over the implicit trie. [typed] should be lowercase; [cost] is the substitution cost of
     * two characters and must return 0 for characters that are equal ignoring case. Whole words within
     * [maxDistance] are returned, plus, for every trie node at least as long as [typed] whose prefix is within
     * [maxCompletionDistance] of it, the [completionLimit] most frequent longer words below it. When
     * [maxDistance] is 2 or more the word's first letter must be the typed first letter, a key next to it, or
     * the typed second letter.
     */
    internal fun search(
        typed: String,
        maxDistance: Double,
        maxCompletionDistance: Double,
        cost: SubstitutionCost,
        completionLimit: Int,
    ): List<Match> {
        val out = ArrayList<Match>()
        if (words.isEmpty() || typed.isEmpty()) return out
        val n = typed.length
        val rows = Array(maxWordLength + 1) { DoubleArray(n + 1) }
        for (j in 0..n) rows[0][j] = j.toDouble()
        val path = CharArray(maxWordLength + 1)
        val band = maxDistance.toInt()
        // Anchor the first letter on wide searches: without it a distance-2 search keeps nearly every 2-3 letter
        // prefix alive and costs ~5x more. Word-initial slips are rare and are still caught when they hit an
        // adjacent key, a swap of the first two letters or one extra leading letter.
        val anchorFirst = maxDistance >= 2.0 && n >= 2

        fun visit(depth: Int, lo: Int, hi: Int, completionDistance: Double) {
            // Children of the node at `depth` (prefix length depth, words[lo, hi)).
            var i = lo
            if (words[i].length == depth) i++
            while (i < hi) {
                val c = words[i][depth]
                var a = i + 1
                var b = hi
                while (a < b) {
                    val mid = (a + b) ushr 1
                    if (words[mid][depth] <= c) a = mid + 1 else b = mid
                }
                val end = a
                if (depth == 0 && anchorFirst && !firstCharAllowed(typed, c, cost)) {
                    i = end
                    continue
                }
                val d = depth + 1
                path[d] = c
                val prev = rows[d - 1]
                val cur = rows[d]
                // Cells with |d - j| > band already exceed maxDistance (each step off the diagonal costs 1).
                cur.fill(Double.POSITIVE_INFINITY)
                cur[0] = d.toDouble()
                var rowMin = cur[0]
                val jFrom = maxOf(1, d - band)
                val jTo = minOf(n, d + band)
                for (j in jFrom..jTo) {
                    val t = typed[j - 1]
                    val sub = if (t == c) 0.0 else cost.substitutionCost(t, c)
                    var v = min(min(prev[j] + 1.0, cur[j - 1] + 1.0), prev[j - 1] + sub)
                    if (j > 1 && d > 1) {
                        val pc = path[d - 1]
                        val tp = typed[j - 2]
                        if (t != c && sameIgnoringCase(t, pc) && sameIgnoringCase(tp, c)) {
                            v = min(v, rows[d - 2][j - 2] + 1.0)
                        }
                    }
                    cur[j] = v
                    if (v < rowMin) rowMin = v
                }
                if (rowMin <= maxDistance) {
                    val whole = cur[n]
                    if (words[i].length == d && whole <= maxDistance) out += Match(i, whole, completion = false)
                    var childCompletion = completionDistance
                    if (d >= n && whole <= maxCompletionDistance && whole < completionDistance) {
                        addCompletions(i, end, d, whole, completionLimit, out)
                        childCompletion = whole
                    }
                    visit(d, i, end, childCompletion)
                }
                i = end
            }
        }

        visit(0, 0, words.size, Double.POSITIVE_INFINITY)
        return out
    }

    /** The [limit] most frequent words in [lo, hi) longer than [depth], as completions at [distance]. */
    private fun addCompletions(lo: Int, hi: Int, depth: Int, distance: Double, limit: Int, out: MutableList<Match>) {
        if (limit <= 0) return
        val start = if (words[lo].length == depth) lo + 1 else lo
        if (start >= hi) return
        if (hi - start <= limit) {
            for (k in start until hi) out += Match(k, distance, completion = true)
            return
        }
        // Small fixed-size selection by frequency (limit is a handful), stable on index.
        val best = IntArray(limit) { -1 }
        var filled = 0
        for (k in start until hi) {
            val f = frequencyAt(k)
            if (filled == limit && f <= frequencyAt(best[limit - 1])) continue
            var pos = if (filled < limit) filled++ else limit - 1
            while (pos > 0 && frequencyAt(best[pos - 1]) < f) {
                best[pos] = best[pos - 1]
                pos--
            }
            best[pos] = k
        }
        for (p in 0 until filled) out += Match(best[p], distance, completion = true)
    }

    private fun firstCharAllowed(typed: String, c: Char, cost: SubstitutionCost): Boolean =
        sameIgnoringCase(typed[0], c) || cost.substitutionCost(typed[0], c) < 1.0 || sameIgnoringCase(typed[1], c)

    private fun sameIgnoringCase(a: Char, b: Char): Boolean = a == b || a.lowercaseChar() == b.lowercaseChar()
}
