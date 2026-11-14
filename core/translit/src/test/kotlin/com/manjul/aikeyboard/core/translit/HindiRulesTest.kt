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
        val romans = ('a'..'z').map { it.toString() } + listOf("kh", "gh", "ch", "sh", "th", "ph", "bh", "dh", "zh") +
            listOf("aa", "ee", "ii", "oo", "uu", "ai", "au")
        val rules = romans.flatMap { r ->
            listOf(r, "x" + r, r + "x").flatMap { s -> s.indices.flatMap { table.matchesAt(s, it) } }
        }.distinct()
        assertTrue(rules.isNotEmpty())
        for (rule in rules) {
            assertTrue(rule.native.all { it in 'ऀ'..'ॿ' }, "rule ${rule.roman} has a native char outside the block")
        }
    }
}
