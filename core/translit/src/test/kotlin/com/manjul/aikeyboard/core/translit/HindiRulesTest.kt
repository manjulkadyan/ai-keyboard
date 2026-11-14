/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HindiRulesTest {
    private val table = RuleTable.load(TranslitLang.HI)

    @Test fun loads() {
        assertEquals(TranslitLang.HI, table.lang)
        assertTrue(table.maxRomanLength >= 2)
    }

    @Test fun coversKeyRomanSequences() {
        for (roman in listOf("kh", "gh", "sh", "aa", "ee")) {
            assertTrue(table.matchesAt(roman + "x", 0).any { it.roman == roman }, "no rule for $roman")
        }
    }

    @Test fun everyNativeStringIsInTheScriptBlock() {
        RuleTableChecks.assertAllNativeInBlock(table, '\u0900'..'\u097F')
    }

    @Test fun rowsAreSortedByRoman() {
        RuleTableChecks.assertSortedByRoman(TranslitLang.HI)
    }

    @Test fun wholeWordAaIsIndependentVowelNotMatra() {
        val natives = table.matchesAt("aa", 0).filter { it.roman == "aa" }.map { it.native }
        assertTrue("\u0906" in natives)
        assertTrue("\u093E" !in natives)
    }
}
