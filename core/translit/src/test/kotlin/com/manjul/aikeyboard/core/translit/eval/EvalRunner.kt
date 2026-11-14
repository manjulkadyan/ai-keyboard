/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translit.eval

import com.manjul.aikeyboard.core.translit.TranslitEngine

/** One `roman<TAB>native` pair of an eval file. */
data class EvalPair(val roman: String, val native: String)

/** Top-1 and top-3 accuracy over [total] pairs, as fractions in 0..1. [misses] are the pairs not in the top 3. */
data class EvalResult(val total: Int, val top1: Int, val top3: Int, val misses: List<EvalPair>) {
    val top1Accuracy: Double get() = if (total == 0) 0.0 else top1.toDouble() / total
    val top3Accuracy: Double get() = if (total == 0) 0.0 else top3.toDouble() / total
}

/**
 * Reads a pair file (spec "Evaluation") and scores an engine on it: a pair counts for top-1 when the engine's first
 * candidate is the native word, and for top-3 when it is among the first three. Lines that start with `#` and blank
 * lines are skipped.
 */
object EvalRunner {
    fun readPairs(lines: Sequence<String>): List<EvalPair> = lines
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapIndexed { i, line ->
            val cols = line.split('\t')
            require(cols.size == 2 && cols[0].isNotEmpty() && cols[1].isNotEmpty()) { "Bad pair at data line ${i + 1}" }
            EvalPair(cols[0], cols[1])
        }
        .toList()

    fun readResource(path: String): List<EvalPair> {
        val stream = EvalRunner::class.java.getResourceAsStream(path) ?: error("missing eval file $path")
        return stream.bufferedReader(Charsets.UTF_8).useLines { readPairs(it) }
    }

    fun run(engine: TranslitEngine, pairs: List<EvalPair>): EvalResult {
        var top1 = 0
        var top3 = 0
        val misses = ArrayList<EvalPair>()
        for (p in pairs) {
            val natives = engine.suggest(p.roman, 3).map { it.native }
            if (natives.firstOrNull() == p.native) top1++
            if (p.native in natives) top3++ else misses += p
        }
        return EvalResult(pairs.size, top1, top3, misses)
    }
}
