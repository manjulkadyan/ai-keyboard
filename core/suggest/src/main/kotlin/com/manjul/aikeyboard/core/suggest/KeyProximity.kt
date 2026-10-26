/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.suggest

import kotlin.math.abs

/** Cost of substituting one character for another; 0 for characters equal ignoring case. Primitive, no boxing. */
fun interface SubstitutionCost {
    fun substitutionCost(a: Char, b: Char): Double

    companion object {
        /** 0 for characters equal ignoring case, 1 otherwise. */
        val UNIFORM: SubstitutionCost = SubstitutionCost { a, b ->
            if (a == b || a.lowercaseChar() == b.lowercaseChar()) 0.0 else 1.0
        }
    }
}

/**
 * Substitution costs from key adjacency on a layout given as row strings (top row first). Keys next to each other
 * in a row, or in neighbouring rows with at most half a key of horizontal offset (rows are staggered half a key per
 * row), cost 0.5; any other pair costs 1.0; the same key (case-insensitive) costs 0. Characters not on the layout
 * cost 1.0.
 */
class KeyProximity(rows: List<String>) : SubstitutionCost {
    // Primitive lookup tables: this runs in the innermost loop of every suggestion search.
    private val keyChars: CharArray
    private val keyRow: IntArray
    private val keyX: DoubleArray
    private val indexByChar: IntArray

    init {
        val chars = ArrayList<Char>()
        val rowsOf = ArrayList<Int>()
        val xs = ArrayList<Double>()
        rows.forEachIndexed { r, row ->
            row.forEachIndexed { c, ch ->
                val lower = ch.lowercaseChar()
                if (lower !in chars) {
                    chars += lower
                    rowsOf += r
                    xs += c + r * ROW_STAGGER
                }
            }
        }
        keyChars = chars.toCharArray()
        keyRow = rowsOf.toIntArray()
        keyX = xs.toDoubleArray()
        val maxChar = chars.maxOfOrNull { it.code } ?: -1
        indexByChar = IntArray(maxChar + 1) { -1 }
        chars.forEachIndexed { i, ch -> indexByChar[ch.code] = i }
    }

    override fun substitutionCost(a: Char, b: Char): Double {
        if (a == b) return 0.0
        val la = a.lowercaseChar()
        val lb = b.lowercaseChar()
        if (la == lb) return 0.0
        val ia = keyIndex(la)
        val ib = keyIndex(lb)
        if (ia < 0 || ib < 0) return 1.0
        val rowGap = abs(keyRow[ia] - keyRow[ib])
        val dx = abs(keyX[ia] - keyX[ib])
        val adjacent = when (rowGap) {
            0 -> dx <= 1.0 + EPS
            1 -> dx <= ROW_STAGGER + EPS
            else -> false
        }
        return if (adjacent) ADJACENT_COST else 1.0
    }

    private fun keyIndex(c: Char): Int = if (c.code < indexByChar.size) indexByChar[c.code] else -1

    companion object {
        private const val ROW_STAGGER = 0.5
        private const val EPS = 1e-6
        private const val ADJACENT_COST = 0.5

        val QWERTY: KeyProximity = KeyProximity(listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"))

        // Row strings copied from the shipped character layouts in app/src/main/assets/ime/keyboard/:
        // org.florisboard.layouts/layouts/characters/arabic.json, org.aikeyboard.layouts/.../urdu_standard.json and
        // org.aikeyboard.layouts/.../hindi_phonetic.json (lower case of each key). Update together with the layouts.
        private val ARABIC = KeyProximity(listOf("ضصثقفغعهخحج", "شسيبلاتنمكط", "ذءؤرىةوزظد"))
        private val URDU = KeyProximity(listOf("ضصثقفغعخحج", "شسیبلاتنمک", "طڈٹردپوگچ", "ذزڑںہءے۔"))
        private val HINDI_PHONETIC = KeyProximity(listOf("टवेरतयुिोप", "ासदडगहजकल", "्षचंबनम"))
        private val UNIFORM = KeyProximity(emptyList())

        fun forLocale(locale: String): KeyProximity =
            when (locale.substringBefore('-').substringBefore('_').lowercase()) {
                "en", "es", "fr", "pt", "id" -> QWERTY
                "ar" -> ARABIC
                "ur" -> URDU
                "hi" -> HINDI_PHONETIC
                else -> UNIFORM
            }
    }
}
