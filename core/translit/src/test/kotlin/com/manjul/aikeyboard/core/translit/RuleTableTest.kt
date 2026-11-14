/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuleTableTest {
    private fun table() = RuleTable.parse(
        TranslitLang.UR,
        sequenceOf("kh\tخ\t0.0\tany", "k\tک\t0.0\tany", "a\tہ\t0.0\tend"),
    )

    @Test fun matchesDigraphAndSingleAtOffsetZero() {
        val romans = table().matchesAt("khana", 0).map { it.roman }.toSet()
        assertEquals(setOf("kh", "k"), romans)
        assertEquals(2, table().maxRomanLength)
    }

    @Test fun inputIsLowerCased() {
        assertEquals(2, table().matchesAt("KHana", 0).size)
    }

    @Test fun endRuleMatchesOnlyAtLastSegment() {
        assertTrue(table().matchesAt("kha", 2).any { it.roman == "a" })
        assertFalse(table().matchesAt("khaa", 2).any { it.roman == "a" })
    }

    @Test fun endAndMidRulesNeverMatchAtOffsetZero() {
        val t = RuleTable.parse(
            TranslitLang.UR,
            sequenceOf("a\t\u0627\t0.0\tstart", "a\t\u06C1\t0.0\tend", "a\t\t0.0\tmid"),
        )
        assertEquals(listOf("\u0627"), t.matchesAt("a", 0).map { it.native })
    }

    @Test fun startRuleMatchesOnlyAtOffsetZero() {
        val t = RuleTable.parse(TranslitLang.UR, sequenceOf("a\tا\t0.0\tstart"))
        assertEquals(1, t.matchesAt("aa", 0).size)
        assertTrue(t.matchesAt("aa", 1).isEmpty())
    }

    @Test fun midRuleExcludesStartAndEnd() {
        val t = RuleTable.parse(TranslitLang.UR, sequenceOf("a\t\t0.0\tmid"))
        assertTrue(t.matchesAt("aaa", 0).isEmpty())
        assertEquals(1, t.matchesAt("aaa", 1).size)
        assertTrue(t.matchesAt("aaa", 2).isEmpty())
    }

    @Test fun digitsAndPunctuationNeverMatch() {
        assertTrue(table().matchesAt("1,k", 0).isEmpty())
        assertTrue(table().matchesAt("1,k", 1).isEmpty())
        assertTrue(table().matchesAt("k", 5).isEmpty())
    }

    @Test fun badLineFailsWithLineNumberAndNoText() {
        val secret = "zzsecret"
        val ex = assertFailsWith<IllegalArgumentException> {
            RuleTable.parse(TranslitLang.UR, sequenceOf("# header", "k\tک\t0.0\tany", "$secret\tx\tnope"))
        }
        assertTrue(ex.message!!.contains("3"))
        assertFalse(ex.message!!.contains(secret))
    }

    @Test fun negativeCostAndUnknownPositionAreRejected() {
        val neg = assertFailsWith<IllegalArgumentException> {
            RuleTable.parse(TranslitLang.UR, sequenceOf("qq\tx\t-1.0\tany"))
        }
        assertFalse(neg.message!!.contains("qq"))
        val pos = assertFailsWith<IllegalArgumentException> {
            RuleTable.parse(TranslitLang.UR, sequenceOf("qq\tx\t1.0\tmiddle"))
        }
        assertFalse(pos.message!!.contains("qq"))
    }

    @Test fun toStringHoldsNoRuleText() {
        assertEquals("RuleTable(lang=ur, rules=3)", table().toString())
    }
}
