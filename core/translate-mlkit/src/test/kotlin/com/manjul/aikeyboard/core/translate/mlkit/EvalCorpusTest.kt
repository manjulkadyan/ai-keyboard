/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate.mlkit

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Shape checks for the ur/hi/ar/bn quality corpus that `TranslateEvalHarness` (androidTest) runs on a device
 * (plan Task 12). Reads the TSV assets straight from the source tree; Gradle runs unit tests in the module dir.
 */
class EvalCorpusTest {
    private val dir = File("src/androidTest/assets/eval")
    private val langs = listOf("ur", "hi", "ar", "bn")
    private val registers = setOf("chat", "email", "everyday", "formal")
    private val header = listOf("id", "direction", "register", "source", "reference")

    private fun rows(lang: String): List<List<String>> {
        val lines = File(dir, "$lang.tsv").readLines(Charsets.UTF_8).filter { it.isNotBlank() && !it.startsWith("#") }
        assertEquals(header, lines.first().split('\t'), "$lang header")
        return lines.drop(1).map { it.split('\t') }
    }

    private fun nativeScript(lang: String, c: Char): Boolean = when (lang) {
        "ur", "ar" -> c in '؀'..'ۿ' || c in 'ݐ'..'ݿ' || c in 'ﭐ'..'﷿' || c in 'ﹰ'..'﻿'
        "hi" -> c in 'ऀ'..'ॿ'
        "bn" -> c in 'ঀ'..'৿'
        else -> false
    }

    @Test fun everyFileHas200EnglishAnd50NativeRows() {
        for (lang in langs) {
            val rows = rows(lang)
            for (row in rows) {
                assertEquals(5, row.size, "$lang ${row.firstOrNull()} column count")
                assertTrue(row[2] in registers, "$lang ${row[0]} register")
                assertTrue(row[3].isNotBlank() && row[3].length <= 200, "$lang ${row[0]} source length")
                assertTrue(row[4].isEmpty(), "$lang ${row[0]} reference is left for raters")
            }
            assertEquals(rows.size, rows.map { it[0] }.toSet().size, "$lang ids unique")
            val forward = rows.filter { it[1] == "en-$lang" }
            val reverse = rows.filter { it[1] == "$lang-en" }
            assertEquals(200, forward.size, "$lang forward rows")
            assertEquals(50, reverse.size, "$lang reverse rows")
            assertEquals(rows.size, forward.size + reverse.size, "$lang directions")
            assertEquals(registers, forward.map { it[2] }.toSet(), "$lang forward covers every register")
            assertEquals(registers, reverse.map { it[2] }.toSet(), "$lang reverse covers every register")
            for (row in reverse) {
                val letters = row[3].filter(Char::isLetter)
                assertTrue(letters.isNotEmpty() && letters.all { nativeScript(lang, it) }, "$lang ${row[0]} native script")
            }
            for (row in forward) {
                assertTrue(row[3].all { it.code < 0x80 || it == '’' }, "$lang ${row[0]} English source is ASCII")
            }
        }
    }

    @Test fun englishSourcesAreTheSameInAllFourFiles() {
        val english = langs.map { lang -> rows(lang).filter { it[1] == "en-$lang" }.map { it[0] to it[3] } }
        for (other in english.drop(1)) assertEquals(english.first(), other)
        assertEquals(200, english.first().map { it.second }.toSet().size, "English sources unique")
    }

    @Test fun ratingTemplateHasTheRubricColumns() {
        val header = File("../../docs/translation/eval-rating-template.csv").readLines(Charsets.UTF_8).first().split(',')
        for (column in listOf("id", "direction", "source", "mlkit_output", "rater", "adequacy", "fluency")) {
            assertTrue(column in header, column)
        }
    }
}
