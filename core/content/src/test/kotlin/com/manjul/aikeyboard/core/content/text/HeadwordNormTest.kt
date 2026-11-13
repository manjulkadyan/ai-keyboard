/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.text

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals

/**
 * The device half of the `headword_norm` contract (plan F5). [CASES] is a verbatim copy of
 * `:tools:packbuild` `HeadwordNormTest.CASES`, so a pack built by the tool and a lookup on the device agree.
 */
class HeadwordNormTest {
    @ParameterizedTest
    @MethodSource("cases")
    fun `normalises like the pack builder`(input: String, expected: String) {
        assertEquals(expected, HeadwordNorm.of(input))
    }

    companion object {
        /** Copied verbatim from `:tools:packbuild` `HeadwordNormTest.CASES`. Keep the two lists identical. */
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
