/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translit

import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Checks shared by the per-language rule table tests. */
internal object RuleTableChecks {
    /** Every native string of every rule is empty or entirely inside [block]. */
    fun assertAllNativeInBlock(table: RuleTable, block: CharRange) {
        assertTrue(table.rules.isNotEmpty())
        table.rules.forEachIndexed { i, rule ->
            assertTrue(rule.native.all { it in block }, "rule #$i (${table.lang.tag}) has a char outside the block")
        }
    }

    /** Rows are sorted by the roman column (plan decision 10). */
    fun assertSortedByRoman(lang: TranslitLang) {
        val romans = RuleTable::class.java.getResourceAsStream("/translit/${lang.tag}.tsv")!!
            .bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.substringBefore('\t') }
        assertEquals(romans.sorted(), romans, "${lang.tag}.tsv is not sorted by roman")
    }
}
