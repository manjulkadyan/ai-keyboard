/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UrduRulesTest {
    private val table = RuleTable.load(TranslitLang.UR)

    @Test fun loads() {
        assertEquals(TranslitLang.UR, table.lang)
        assertTrue(table.maxRomanLength >= 2)
    }

    @Test fun coversKeyRomanSequences() {
        for (roman in listOf("kh", "gh", "sh", "aa", "ee")) {
            assertTrue(table.matchesAt(roman + "x", 0).any { it.roman == roman }, "no rule for $roman")
        }
    }

    @Test fun everyNativeStringIsInTheScriptBlock() {
        RuleTableChecks.assertAllNativeInBlock(table, '\u0600'..'\u06FF')
    }

    @Test fun rowsAreSortedByRoman() {
        RuleTableChecks.assertSortedByRoman(TranslitLang.UR)
    }

    @Test fun wholeWordVowelsGetNoEndingForms() {
        val e = table.matchesAt("e", 0).map { it.native }
        assertTrue("\u0627" in e && "\u06D2" !in e)
        val a = table.matchesAt("a", 0).map { it.native }
        assertTrue("\u0627" in a && "\u06C1" !in a)
    }
}
