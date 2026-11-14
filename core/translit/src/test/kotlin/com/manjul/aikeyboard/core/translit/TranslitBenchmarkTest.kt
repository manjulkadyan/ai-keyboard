/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translit

import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JVM guard for the decoder: 1,000 Roman words against the shipped dictionaries in under 3,000 ms (3 ms a word, a
 * 10x margin under the 30 ms device budget). The device p90 is checked on device, not here.
 */
@Tag("benchmark")
class TranslitBenchmarkTest {
    private val words: List<String> =
        (TranslitBenchmarkTest::class.java.getResourceAsStream("/bench/roman-inputs.txt") ?: error("missing inputs"))
            .bufferedReader(Charsets.UTF_8)
            .useLines { lines -> lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList() }

    @Test fun thousandWordsUnderThreeSeconds() {
        assertEquals(1_000, words.size)
        for (lang in TranslitLang.entries) {
            val engine = TranslitEngine(lang, RuleTable.load(lang), TestLexicons.shipped(lang), CustomMappings())
            words.take(200).forEach { engine.suggest(it, 3) } // JIT warm-up
            var empty = 0
            val start = System.nanoTime()
            for (w in words) if (engine.suggest(w, 3).isEmpty()) empty++
            val ms = (System.nanoTime() - start) / 1_000_000
            println("TranslitBenchmark lang=${lang.tag} words=${words.size} totalMs=$ms emptyResults=$empty")
            assertTrue(ms < 3_000, "lang=${lang.tag} took $ms ms")
            assertEquals(0, empty, "lang=${lang.tag} words without a candidate")
        }
    }
}
