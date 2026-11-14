/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translit

import java.util.Locale

/**
 * Roman to native rules for one language, indexed by the first roman character. Rule text is user-independent data,
 * but inputs are user content: nothing here logs or puts input text into an exception message.
 */
class RuleTable private constructor(val lang: TranslitLang, internal val rules: List<Rule>) {
    private val byFirstChar: Map<Char, List<Rule>> = rules.groupBy { it.roman[0] }

    val maxRomanLength: Int = rules.maxOfOrNull { it.roman.length } ?: 0

    /**
     * Rules whose roman part starts at input[offset]. Position is checked against offset and input length. MID and
     * END never match at offset 0, so a whole-word vowel gets its word-start form only. Only the window a rule can
     * cover is lower-cased, char by char, so the length never changes (no lost matches for input such as "İ").
     */
    fun matchesAt(input: String, offset: Int): List<Rule> {
        if (offset < 0 || offset >= input.length) return emptyList()
        val end = minOf(input.length, offset + maxRomanLength)
        return matches(lowerCase(input.substring(offset, end)), 0, offset, input.length)
    }

    /** [matchesAt] for a [lowerCase]d word. The decoder lower-cases once per word, not once per call. */
    internal fun matchesAtLowered(text: String, offset: Int): List<Rule> {
        if (offset < 0 || offset >= text.length) return emptyList()
        return matches(text, offset, offset, text.length)
    }

    /** Rules matching [text] at [at]; [offset] and [length] place that point in the whole input. */
    private fun matches(text: String, at: Int, offset: Int, length: Int): List<Rule> {
        val candidates = byFirstChar[text[at]] ?: return emptyList()
        return candidates.filter { rule ->
            text.startsWith(rule.roman, at) &&
                positionAllows(rule.position, offset, offset + rule.roman.length, length)
        }
    }

    private fun positionAllows(position: Position, offset: Int, end: Int, length: Int): Boolean = when (position) {
        Position.ANY -> true
        Position.START -> offset == 0
        Position.MID -> offset > 0 && end < length
        Position.END -> offset > 0 && end == length
    }

    override fun toString(): String = "RuleTable(lang=${lang.tag}, rules=${rules.size})"

    companion object {
        /** Lower-cases one char at a time, so the result is as long as [input] and offsets stay valid. */
        internal fun lowerCase(input: String): String {
            val chars = CharArray(input.length) { input[it].lowercaseChar() }
            return String(chars)
        }

        /** Columns: roman, native, cost, position. Blank lines and lines starting with # are skipped. */
        fun parse(lang: TranslitLang, tsv: Sequence<String>): RuleTable {
            val rules = ArrayList<Rule>()
            tsv.forEachIndexed { index, line ->
                if (line.isBlank() || line.startsWith("#")) return@forEachIndexed
                val n = index + 1
                val cols = line.split('\t')
                require(cols.size == 4) { "Bad column count at line $n" }
                val roman = cols[0]
                require(roman.isNotEmpty()) { "Empty roman part at line $n" }
                require(roman == roman.lowercase(Locale.ROOT)) { "Roman part not lower-case at line $n" }
                val cost = cols[2].toDoubleOrNull()
                require(cost != null && cost >= 0.0 && cost.isFinite()) { "Bad cost at line $n" }
                val position = when (cols[3]) {
                    "any" -> Position.ANY
                    "start" -> Position.START
                    "mid" -> Position.MID
                    "end" -> Position.END
                    else -> throw IllegalArgumentException("Unknown position at line $n")
                }
                rules += Rule(roman, cols[1], cost, position)
            }
            return RuleTable(lang, rules)
        }

        fun load(lang: TranslitLang): RuleTable {
            val stream = RuleTable::class.java.getResourceAsStream("/translit/${lang.tag}.tsv")
                ?: error("Missing rule table resource for ${lang.tag}")
            return stream.bufferedReader(Charsets.UTF_8).use { parse(lang, it.lineSequence().toList().asSequence()) }
        }
    }
}
