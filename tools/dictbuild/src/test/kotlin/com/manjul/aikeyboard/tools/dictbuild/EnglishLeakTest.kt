/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.tools.dictbuild

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnglishLeakTest {
    @Test
    fun `share counts entries that are much more frequent in english`() {
        assertEquals(0.5, EnglishLeak.share(mapOf("hola" to 100, "the" to 90), en = mapOf("the" to 1000)))
    }

    @Test
    fun `an entry within 2x of its english rate is not a leak`() {
        // "no": 50% of the list, 50% of en -> not more than 2x.
        assertEquals(0.0, EnglishLeak.share(mapOf("no" to 100, "si" to 100), en = mapOf("no" to 10, "yes" to 10)))
    }

    @Test
    fun `empty list has zero share`() {
        assertEquals(0.0, EnglishLeak.share(emptyMap(), en = mapOf("the" to 1)))
    }

    @Test
    fun `isEnglish matches share`() {
        val list = mapOf("hola" to 100, "the" to 90)
        val en = mapOf("the" to 1000)
        assertTrue(EnglishLeak.isEnglish("the", list, en))
        assertFalse(EnglishLeak.isEnglish("hola", list, en))
    }

    @Test
    fun `removeLeaks leaves no leak`() {
        val list = mapOf("hola" to 1000, "the" to 90, "and" to 50, "casa" to 400, "you" to 30)
        val en = mapOf("the" to 1000, "and" to 900, "you" to 800, "casa" to 1)
        val cleaned = EnglishLeak.removeLeaks(list, en)
        assertEquals(setOf("hola", "casa"), cleaned.keys)
        assertEquals(0.0, EnglishLeak.share(cleaned, en))
    }

    @Test
    fun `removeLeaks with a budget drops the most english entries first`() {
        // 10 entries, 3 leaks; a 0.2 budget keeps 2 leaks at most. "the" (absent-ish here) goes first.
        val list = (1..7).associate { "w$it" to 1000 } + mapOf("the" to 1, "place" to 400, "you" to 2)
        val en = mapOf("the" to 100_000, "place" to 20_000, "you" to 50_000, "w1" to 1)
        val cleaned = EnglishLeak.removeLeaks(list, en, maxShare = 0.2)
        assertTrue("place" in cleaned)
        assertFalse("the" in cleaned)
        assertTrue(EnglishLeak.share(cleaned, en) <= 0.2)
    }
}
