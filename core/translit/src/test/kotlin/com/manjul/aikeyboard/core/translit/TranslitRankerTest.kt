/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TranslitRankerTest {
    private val lexicon = MapLexicon(mapOf("known" to 100, "other" to 100))
    private val ranker = TranslitRanker(TranslitLang.HI, lexicon)

    @Test fun lexiconWordBeatsACheaperRuleOnlySpelling() {
        val out = ranker.rank(listOf(Hypothesis("rules", 0.0), Hypothesis("known", 1.0)), null) { 0 }
        assertEquals(listOf("known", "rules"), out.map { it.native })
        assertEquals(listOf(Source.LEXICON, Source.RULES), out.map { it.source })
    }

    @Test fun mappingBeatsBoth() {
        val hyps = listOf(Hypothesis("rules", 0.0), Hypothesis("known", 1.0))
        val out = ranker.rank(hyps, "mine") { 0 }
        assertEquals(listOf("mine", "known", "rules"), out.map { it.native })
        assertEquals(Source.MAPPING, out.first().source)
    }

    @Test fun mappingThatIsAlsoAHypothesisIsMergedAsMapping() {
        val out = ranker.rank(listOf(Hypothesis("rules", 0.0), Hypothesis("known", 1.0)), "known") { 0 }
        assertEquals(listOf("known", "rules"), out.map { it.native })
        assertEquals(Source.MAPPING, out.first().source)
    }

    @Test fun boostMovesACandidateUpByOnePlace() {
        val hyps = listOf(Hypothesis("a", 0.0), Hypothesis("b", 0.5), Hypothesis("c", 1.2))
        assertEquals(listOf("a", "b", "c"), ranker.rank(hyps, null) { 0 }.map { it.native })
        val boosted = ranker.rank(hyps, null) { if (it == "c") 1 else 0 }
        assertEquals(listOf("a", "c", "b"), boosted.map { it.native })
    }

    @Test fun duplicateHypothesesAreMergedKeepingTheBestScore() {
        val out = ranker.rank(listOf(Hypothesis("known", 0.5), Hypothesis("known", 0.1)), null) { 0 }
        assertEquals(1, out.size)
        assertEquals(-0.1 + TranslitRanker.LEXICON_BONUS + 100 / 255.0, out.single().score, 1e-9)
    }

    @Test fun urduWeighsRuleCostMoreThanHindi() {
        val hyp = listOf(Hypothesis("rules", 1.0))
        val hiScore = ranker.rank(hyp, null) { 0 }.single().score
        val urScore = TranslitRanker(TranslitLang.UR, lexicon).rank(hyp, null) { 0 }.single().score
        assertEquals(-1.0, hiScore, 1e-9)
        assertEquals(-1.6, urScore, 1e-9)
    }

    @Test fun equalScoreTieGoesToFewerSegmentsThenNative() {
        val hyps = listOf(Hypothesis("other", 0.0, segments = 4), Hypothesis("known", 0.0, segments = 1))
        for (order in listOf(hyps, hyps.reversed())) {
            assertEquals(listOf("known", "other"), ranker.rank(order, null) { 0 }.map { it.native })
        }
        // Equal score and segments: the native string decides, not the hash order of the merge map.
        val natives = listOf("zeta", "q", "mu", "alpha", "kappa", "b", "yy", "epsilon", "c9", "nu")
        val same = natives.map { Hypothesis(it, 0.0, 2) }
        for (order in listOf(same, same.reversed(), same.shuffled(java.util.Random(3)))) {
            assertEquals(natives.sorted(), ranker.rank(order, null) { 0 }.map { it.native })
        }
    }

    @Test fun candidateToStringHoldsNoText() {
        val c = ranker.rank(listOf(Hypothesis("known", 0.0)), null) { 0 }.single()
        assertEquals("Candidate(source=LEXICON)", c.toString())
        assertTrue("known" !in ranker.toString())
    }
}
