/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.tools.packbuild

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals

/**
 * Pins `headword_norm` (NFC, then lower case with `Locale.ROOT`, then trim). `:core:content` `HeadwordNorm` is a
 * deliberate copy (plan F5): its `HeadwordNormTest` must use exactly [CASES], so the two cannot drift.
 */
class HeadwordNormTest {
    @ParameterizedTest
    @MethodSource("cases")
    fun `normalises like the device`(input: String, expected: String) {
        assertEquals(expected, HeadwordNorm.of(input))
    }

    companion object {
        /**
         * Input to expected output. Copy this list verbatim into `:core:content` `HeadwordNormTest`. Every non-ASCII
         * character is a `\u` escape, so an editor or a paste cannot normalise the NFD cases away.
         */
        val CASES: List<Pair<String, String>> = listOf(
            "run" to "run",
            "Run" to "run",
            " run " to "run",
            "RUN\t\n" to "run",
            "Caf\u00e9" to "caf\u00e9", // NFC input: precomposed e-acute
            "Cafe\u0301" to "caf\u00e9", // NFD input (e + combining acute) composes to NFC
            "CAFE\u0301" to "caf\u00e9", // NFD upper case
            "\u0130stanbul" to "i\u0307stanbul", // Locale.ROOT, not Turkish: dotted capital I gives i + combining dot
            "I" to "i", // Locale.ROOT: never dotless \u0131
            "Stra\u00dfe" to "stra\u00dfe", // lower case only, no case folding to "ss"
            "ice cream" to "ice cream", // inner spaces stay
            "" to "",
            "   " to "",
        )

        @JvmStatic
        fun cases(): List<Array<String>> = CASES.map { arrayOf(it.first, it.second) }
    }
}
