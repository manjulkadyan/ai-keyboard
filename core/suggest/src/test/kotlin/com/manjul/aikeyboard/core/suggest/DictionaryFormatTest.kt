/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.suggest

import com.manjul.aikeyboard.core.suggest.testing.TestDictionaries
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DictionaryFormatTest {
    private fun bytes(
        unigrams: List<Pair<String, Int>>,
        bigrams: List<Triple<String, String, Int>> = emptyList(),
        locale: String = "en",
    ): ByteArray = ByteArrayOutputStream().also { DictionaryFormat.write(it, locale, unigrams, bigrams) }.toByteArray()

    @Test fun roundTripsWordsAndBigrams() {
        val unigrams = TestDictionaries.syntheticUnigrams(1_000, seed = 7)
        val words = unigrams.map { it.first }
        val bigrams = (0 until 10).map { Triple(words[it], words[it + 1], 100 * (it + 1)) }

        val dict = DictionaryFormat.read(ByteArrayInputStream(bytes(unigrams, bigrams, locale = "es")))

        assertEquals("es", dict.locale)
        assertEquals(1_000, dict.size)
        for ((word, _) in unigrams) assertTrue(dict.containsExact(word), "word missing after round trip")
        for ((prev, word, _) in bigrams) assertTrue(dict.bigram(prev, word) > 0, "bigram missing after round trip")
        assertEquals(0, dict.bigram(words[20], words[21]))
        assertFalse(dict.containsExact("notaword-zz"))
        assertEquals(0, dict.frequency("notaword-zz"))
    }

    @Test fun frequenciesKeepTheirOrder() {
        val unigrams = TestDictionaries.syntheticUnigrams(1_000, seed = 11)
        val dict = DictionaryFormat.read(ByteArrayInputStream(bytes(unigrams)))
        val sorted = unigrams.sortedBy { it.second }
        for (i in 1 until sorted.size) {
            assertTrue(dict.frequency(sorted[i - 1].first) <= dict.frequency(sorted[i].first), "frequency order broken")
        }
        assertEquals(255, dict.frequency(sorted.last().first))
        assertTrue(sorted.all { dict.frequency(it.first) in 1..255 })
    }

    @Test fun bigramFrequenciesKeepTheirOrder() {
        val unigrams = listOf("a" to 1, "b" to 1, "c" to 1)
        val dict = DictionaryFormat.read(
            ByteArrayInputStream(bytes(unigrams, listOf(Triple("a", "b", 5), Triple("a", "c", 500)))),
        )
        assertTrue(dict.bigram("a", "b") < dict.bigram("a", "c"))
    }

    @Test fun duplicateAndEmptyWordsAreMerged() {
        val dict = DictionaryFormat.read(ByteArrayInputStream(bytes(listOf("b" to 5, "a" to 3, "b" to 9, "" to 4))))
        assertEquals(2, dict.size)
    }

    @Test fun lookupFallsBackToLowercase() {
        val dict = TestDictionaries.en()
        assertTrue(dict.containsExact("The"))
        assertEquals(dict.frequency("the"), dict.frequency("THE"))
    }

    @Test fun badMagicThrowsWithoutContent() {
        val payload = "SECRETWORDS-hello-there".toByteArray()
        val e = assertFailsWith<IllegalArgumentException> { DictionaryFormat.read(ByteArrayInputStream(payload)) }
        assertNoContent(e, payload)
    }

    @Test fun unknownVersionThrowsWithoutContent() {
        val good = bytes(listOf("secretword" to 10))
        good[4] = 99
        val e = assertFailsWith<IllegalArgumentException> { DictionaryFormat.read(ByteArrayInputStream(good)) }
        assertNoContent(e, good)
    }

    @Test fun truncatedThrowsWithoutContent() {
        val good = bytes(listOf("secretword" to 10, "anotherword" to 3))
        val cut = good.copyOf(good.size - 6)
        val e = assertFailsWith<IllegalArgumentException> { DictionaryFormat.read(ByteArrayInputStream(cut)) }
        assertNoContent(e, cut)
    }

    private fun assertNoContent(e: Throwable, input: ByteArray) {
        val message = e.message.orEmpty()
        val text = String(input, Charsets.ISO_8859_1)
        for (token in listOf("SECRET", "secret", "hello", "there", "another", text)) {
            assertFalse(message.contains(token), "exception message echoes input")
        }
        assertFalse(message.contains("99"), "exception message echoes input bytes")
        assertEquals(null, e.cause, "cause could carry input details")
    }

    @Test fun writeScaledKeepsTheGivenFrequencyBytes() {
        val out = ByteArrayOutputStream()
        DictionaryFormat.writeScaled(out, "ur", listOf("ب" to 16, "ا" to 16, "ا" to 20))
        val dict = DictionaryFormat.read(ByteArrayInputStream(out.toByteArray()))
        assertEquals(listOf("ا", "ب"), dict.wordList())
        assertEquals(20, dict.frequency("ا"))
        assertEquals(16, dict.frequency("ب"))
        assertEquals(0, dict.bigram("ا", "ب"))
        assertFailsWith<IllegalArgumentException> { DictionaryFormat.writeScaled(ByteArrayOutputStream(), "x", listOf("a" to 0)) }
    }

    @Test fun scaleCountMatchesWrite() {
        assertEquals(255, DictionaryFormat.scaleCount(1000, 1000))
        assertEquals(0, DictionaryFormat.scaleCount(0, 1000))
        assertTrue(DictionaryFormat.scaleCount(3, 1000) in 1..254)
    }
}
