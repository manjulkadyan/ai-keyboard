/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translit.eval

import com.manjul.aikeyboard.core.translit.CustomMappings
import com.manjul.aikeyboard.core.translit.MapLexicon
import com.manjul.aikeyboard.core.translit.RuleTable
import com.manjul.aikeyboard.core.translit.TestLexicons
import com.manjul.aikeyboard.core.translit.TranslitEngine
import com.manjul.aikeyboard.core.translit.TranslitLang
import org.junit.jupiter.api.TestReporter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The S6 smoke sets against the shipped rule tables and dictionaries (plan decision 12). A regression floor, not the
 * PRD gate: each floor is the accuracy measured at the end of Task 6 minus 3 points, rounded down.
 */
class SmokeSetTest {
    private fun measure(lang: TranslitLang, reporter: TestReporter): EvalResult {
        val engine = TranslitEngine(lang, RuleTable.load(lang), TestLexicons.shipped(lang), CustomMappings())
        val result = EvalRunner.run(engine, EvalRunner.readResource("/eval/${lang.tag}-smoke.tsv"))
        val line = "SmokeSet lang=${lang.tag} pairs=${result.total} " +
            "top1=${"%.1f".format(result.top1Accuracy * 100)}% top3=${"%.1f".format(result.top3Accuracy * 100)}%"
        println(line)
        // The smoke pairs are our own test data, not user content, so the misses can be printed for tuning.
        result.misses.forEach { println("SmokeSet miss lang=${lang.tag} ${it.roman} -> ${it.native}") }
        reporter.publishEntry("${lang.tag}.top1", "%.1f".format(result.top1Accuracy * 100))
        reporter.publishEntry("${lang.tag}.top3", "%.1f".format(result.top3Accuracy * 100))
        return result
    }

    private fun assertFloor(lang: TranslitLang, top1Floor: Double, top3Floor: Double, reporter: TestReporter) {
        val r = measure(lang, reporter)
        assertTrue(r.total >= MIN_PAIRS, "lang=${lang.tag} has only ${r.total} pairs")
        assertTrue(r.top1Accuracy * 100 >= top1Floor, "lang=${lang.tag} top-1 below the floor $top1Floor")
        assertTrue(r.top3Accuracy * 100 >= top3Floor, "lang=${lang.tag} top-3 below the floor $top3Floor")
    }

    @Test fun urduAtOrAboveTheFloor(reporter: TestReporter) =
        assertFloor(TranslitLang.UR, UR_TOP1_FLOOR, UR_TOP3_FLOOR, reporter)

    @Test fun hindiAtOrAboveTheFloor(reporter: TestReporter) =
        assertFloor(TranslitLang.HI, HI_TOP1_FLOOR, HI_TOP3_FLOOR, reporter)

    @Test fun pairFilesParseAndHaveTheirHeader() {
        for (lang in TranslitLang.entries) {
            val path = "/eval/${lang.tag}-smoke.tsv"
            val first = SmokeSetTest::class.java.getResourceAsStream(path)!!.bufferedReader().use { it.readLine() }
            assertTrue(first.startsWith("#") && "AI Keyboard contributors" in first && "Apache" in first, path)
            val pairs = EvalRunner.readResource(path)
            assertEquals(pairs.size, pairs.map { it.roman }.toSet().size, "duplicate roman word in $path")
        }
    }

    @Test fun runnerCountsTopOneAndTopThree() {
        val pairs = EvalRunner.readPairs(sequenceOf("# header", "", "k\tک", "k\tق", "k\tx"))
        assertEquals(listOf(EvalPair("k", "ک"), EvalPair("k", "ق"), EvalPair("k", "x")), pairs)
        val table = RuleTable.parse(TranslitLang.UR, sequenceOf("k\tک\t0.0\tany", "k\tق\t1.0\tany"))
        val engine = TranslitEngine(TranslitLang.UR, table, MapLexicon(emptyMap()), CustomMappings())
        val r = EvalRunner.run(engine, pairs)
        assertEquals(EvalResult(3, top1 = 1, top3 = 2, misses = listOf(EvalPair("k", "x"))), r)
    }

    companion object {
        const val MIN_PAIRS = 200

        // Measured minus 3 points, rounded down (plan decision 12). Measured: ur 63.2/90.6, hi 88.3/95.2.
        const val UR_TOP1_FLOOR = 60.0
        const val UR_TOP3_FLOOR = 87.0
        const val HI_TOP1_FLOOR = 85.0
        const val HI_TOP3_FLOOR = 92.0
    }
}
