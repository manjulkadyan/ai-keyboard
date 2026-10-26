/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.suggest

import kotlin.math.abs
import kotlin.math.min

/** Weighted optimal-string-alignment (restricted Damerau-Levenshtein) distance. */
object DamerauLevenshtein {
    /**
     * Distance from [a] to [b]. Insertions, deletions and adjacent transpositions cost 1.0; a substitution of two
     * different characters costs [cost] (equal characters cost 0). Returns [Double.POSITIVE_INFINITY] once the
     * distance exceeds [max].
     */
    fun distance(a: String, b: String, max: Int = 2, cost: (Char, Char) -> Double = { _, _ -> 1.0 }): Double {
        val limit = max.toDouble()
        if (abs(a.length - b.length) > limit) return Double.POSITIVE_INFINITY
        val n = b.length
        var prevPrev = DoubleArray(n + 1)
        var prev = DoubleArray(n + 1) { it.toDouble() }
        var cur = DoubleArray(n + 1)
        for (i in 1..a.length) {
            cur[0] = i.toDouble()
            var rowMin = cur[0]
            val ca = a[i - 1]
            for (j in 1..n) {
                val cb = b[j - 1]
                val sub = if (ca == cb) 0.0 else cost(ca, cb)
                var d = min(min(prev[j] + 1.0, cur[j - 1] + 1.0), prev[j - 1] + sub)
                if (i > 1 && j > 1 && ca == b[j - 2] && a[i - 2] == cb && ca != cb) {
                    d = min(d, prevPrev[j - 2] + 1.0)
                }
                cur[j] = d
                if (d < rowMin) rowMin = d
            }
            if (rowMin > limit) return Double.POSITIVE_INFINITY
            val recycled = prevPrev
            prevPrev = prev
            prev = cur
            cur = recycled
        }
        val result = prev[n]
        return if (result > limit) Double.POSITIVE_INFINITY else result
    }
}
