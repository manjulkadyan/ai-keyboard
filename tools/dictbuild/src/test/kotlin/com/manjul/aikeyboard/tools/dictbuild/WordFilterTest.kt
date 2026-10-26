/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.tools.dictbuild

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WordFilterTest {
    @Test
    fun `keeps plain words in the right script`() {
        assertTrue(WordFilter.keep("en", "hello"))
        assertTrue(WordFilter.keep("es", "mañana"))
        assertTrue(WordFilter.keep("fr", "déjà"))
        assertTrue(WordFilter.keep("pt", "ação"))
        assertTrue(WordFilter.keep("id", "terima"))
        assertTrue(WordFilter.keep("ar", "مرحبا"))
        assertTrue(WordFilter.keep("ur", "شکریہ"))
        assertTrue(WordFilter.keep("hi", "नमस्ते"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["abc1", "1st", "a.b", "a-b", "hi!", "¿que", "a,b", "a_b", "a b", "\"x", ""])
    fun `rejects digits and punctuation`(word: String) {
        for (locale in listOf("en", "es", "fr", "pt", "id")) assertFalse(WordFilter.keep(locale, word), "$locale")
    }

    @Test
    fun `apostrophes only for en fr and pt`() {
        assertTrue(WordFilter.keep("en", "don't"))
        assertTrue(WordFilter.keep("fr", "aujourd'hui"))
        assertTrue(WordFilter.keep("pt", "d'água"))
        assertFalse(WordFilter.keep("es", "don't"))
        assertFalse(WordFilter.keep("id", "don't"))
        assertFalse(WordFilter.keep("en", "'"))
    }

    @Test
    fun `rejects native-script digits and punctuation`() {
        assertFalse(WordFilter.keep("ar", "٣"))
        assertFalse(WordFilter.keep("ar", "؟"))
        assertFalse(WordFilter.keep("ur", "۲۰"))
        assertFalse(WordFilter.keep("ur", "شکریہ،"))
        assertFalse(WordFilter.keep("hi", "।"))
        assertFalse(WordFilter.keep("hi", "२०"))
    }

    @Test
    fun `rejects wrong script`() {
        for (locale in listOf("ar", "ur", "hi")) assertFalse(WordFilter.keep(locale, "hello"), locale)
        for (locale in listOf("en", "es", "fr", "pt", "id")) {
            assertFalse(WordFilter.keep(locale, "مرحبا"), locale)
            assertFalse(WordFilter.keep(locale, "नमस्ते"), locale)
        }
        assertFalse(WordFilter.keep("ar", "नमस्ते"))
        assertFalse(WordFilter.keep("hi", "مرحبا"))
        assertFalse(WordFilter.keep("en", "привет"))
    }

    @Test
    fun `rejects tokens longer than 24 chars`() {
        assertTrue(WordFilter.keep("en", "a".repeat(24)))
        assertFalse(WordFilter.keep("en", "a".repeat(25)))
    }

    @Test
    fun `rejects entries with count below 3`() {
        assertFalse(WordFilter.keep("en", "hello", count = 2))
        assertTrue(WordFilter.keep("en", "hello", count = 3))
    }

    @Test
    fun `rejects the built-in block list`() {
        for (locale in listOf("en", "es", "fr", "pt", "id")) {
            val list = WordFilter.blockList(locale)
            assertTrue(list.isNotEmpty() && list.size <= 50, locale)
            for (w in list) assertFalse(WordFilter.keep(locale, w), "$locale block list")
        }
        assertFalse(WordFilter.keep("en", "FUCK"))
    }

    @Test
    fun `unknown locale keeps nothing`() {
        assertFalse(WordFilter.keep("de", "hallo"))
    }
}
