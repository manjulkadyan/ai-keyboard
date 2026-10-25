/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LatencyCheckTest {
    @Test fun pairsAreTheTrdDayOneSet() {
        assertEquals(
            listOf("en-ur", "en-hi", "en-ar", "en-bn", "en-es", "ur-en", "hi-en"),
            LatencyCheck.Pairs.map { "${it.source}-${it.target}" },
        )
    }

    @Test fun corpusDropsBlankLinesAndComments() {
        val text = "# authored by us\n\nHello there.\n  How are you?  \n#skip\n"
        assertEquals(listOf("Hello there.", "How are you?"), LatencyCheck.parseCorpus(text))
    }

    @Test fun budgetPerTier() {
        assertEquals(150L, LatencyCheck.p90BudgetMs("mid"))
        assertEquals(400L, LatencyCheck.p90BudgetMs("low"))
        assertNull(LatencyCheck.p90BudgetMs(null))
        assertFailsWith<IllegalArgumentException> { LatencyCheck.p90BudgetMs("high") }
    }

    @Test fun parsePairsFilter() {
        assertEquals(LatencyCheck.Pairs, LatencyCheck.parsePairs(null))
        assertEquals(LatencyCheck.Pairs, LatencyCheck.parsePairs(""))
        assertEquals(
            listOf(LangPair(Lang("en"), Lang("ur")), LangPair(Lang("hi"), Lang("en"))),
            LatencyCheck.parsePairs("en-ur, hi-en"),
        )
        assertFailsWith<IllegalArgumentException> { LatencyCheck.parsePairs("en") }
    }

    @Test fun summaryLineIsNumbersOnly() {
        val line = LatencyCheck.summaryLine(LangPair(Lang("en"), Lang("ur")), (1L..100L).toList())
        assertEquals("en-ur p50=50 p90=90 n=100", line)
    }

    @Test fun withinBudget() {
        assertTrue(LatencyCheck.withinBudget(listOf(100L, 150L), 150L))
        assertFalse(LatencyCheck.withinBudget(listOf(100L, 151L), 150L))
    }
}
