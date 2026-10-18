/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.voice

/** Replaces spoken punctuation words ("comma") with the symbol. Whole words only, Unicode aware. */
object SpokenPunctuation {
    private const val NL = "\n"

    private val table: Map<String, List<Pair<String, String>>> = mapOf(
        "en" to listOf("comma" to ",", "full stop" to ".", "period" to ".", "question mark" to "?", "new line" to NL),
        "ur" to listOf("کاما" to "،", "سوالیہ نشان" to "؟", "نئی لائن" to NL),
        "hi" to listOf("अल्पविराम" to ",", "पूर्ण विराम" to "।", "नई लाइन" to NL),
        "ar" to listOf("فاصلة" to "،", "علامة استفهام" to "؟", "سطر جديد" to NL),
        "es" to listOf("coma" to ",", "punto" to ".", "nueva línea" to NL),
        "pt" to listOf("vírgula" to ",", "ponto final" to ".", "nova linha" to NL),
        "fr" to listOf("virgule" to ",", "point" to ".", "à la ligne" to NL),
        "id" to listOf("koma" to ",", "titik" to ".", "baris baru" to NL),
    )

    private class Rule(val regex: Regex, val replacement: String)

    // Longest phrase first so "ponto final" wins over a shorter overlapping entry.
    private val rules: Map<String, List<Rule>> = table.mapValues { (_, entries) ->
        entries.sortedByDescending { it.first.length }.map { (word, symbol) ->
            val phrase = word.split(' ').joinToString("""\s+""") { Regex.escape(it) }
            // Lookarounds on letters/marks/digits instead of \b, which is ASCII-centric for Urdu, Hindi, Arabic.
            val trailing = if (symbol == NL) """[ \t]*""" else ""
            Rule(
                Regex("""[ \t]*(?<![\p{L}\p{M}\p{N}])$phrase(?![\p{L}\p{M}\p{N}])$trailing""", setOf(RegexOption.IGNORE_CASE)),
                symbol,
            )
        }
    }

    fun apply(text: String, lang: String): String {
        val key = lang.substringBefore('-').substringBefore('_').lowercase()
        val langRules = rules[key] ?: return text
        var out = text
        for (rule in langRules) {
            out = rule.regex.replace(out) { rule.replacement }
        }
        return out
    }
}
