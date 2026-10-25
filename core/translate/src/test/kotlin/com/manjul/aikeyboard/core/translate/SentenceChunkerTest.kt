/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SentenceChunkerTest {
    private val tokens = listOf(
        "Hello world", "how are you", "کیا حال ہے", "میں ٹھیک ہوں", "مرحبا بالعالم", "आप कैसे हैं", "আমি ভালো আছি",
        "3.14", "e.g.", "؟", "۔", "।", "!", "?", ".", "…", "\"", ")", " ", "  ", "\t", "\n", "\r\n", "\n\n", "😀",
        "👍🏽", " ", "a", "word",
    )

    private fun roundTrip(text: String, maxChars: Int = SentenceChunker.DEFAULT_MAX_CHARS) {
        val chunks = SentenceChunker.split(text, maxChars)
        assertEquals(text, SentenceChunker.join(chunks, chunks.map { it.body }), "round trip")
        for (c in chunks) {
            assertTrue(c.separatorAfter.all { it.isWhitespace() }, "separator is whitespace only")
            assertTrue(c.body.length <= maxChars || c.body.none { it.isWhitespace() }, "chunk over maxChars")
            assertTrue('\n' !in c.body && '\r' !in c.body, "newline inside a chunk body")
        }
    }

    @Test fun randomTextRoundTripsExactly() {
        val random = Random(20261025)
        repeat(500) {
            val text = buildString { repeat(random.nextInt(0, 40)) { append(tokens.random(random)) } }
            roundTrip(text)
            roundTrip(text, maxChars = random.nextInt(1, 30))
        }
    }

    @Test fun urduSentencesSplitInTwo() {
        val chunks = SentenceChunker.split("کیا حال ہے؟ میں ٹھیک ہوں۔")
        assertEquals(listOf(Chunk("کیا حال ہے؟", " "), Chunk("میں ٹھیک ہوں۔", "")), chunks)
    }

    @Test fun hindiAndEnglishTerminators() {
        assertEquals(2, SentenceChunker.split("आप कैसे हैं। मैं ठीक हूँ।").size)
        assertEquals(listOf("Hi!", "Is it?", "Yes."), SentenceChunker.split("Hi! Is it? Yes.").map { it.body })
    }

    @Test fun terminatorWithoutFollowingSpaceDoesNotSplit() {
        assertEquals(listOf("Pi is 3.14 today."), SentenceChunker.split("Pi is 3.14 today.").map { it.body })
    }

    @Test fun paragraphBreaksStayInSeparators() {
        val chunks = SentenceChunker.split("  One.\n\nTwo\r\nThree  ")
        assertEquals(
            listOf(Chunk("", "  "), Chunk("One.", "\n\n"), Chunk("Two", "\r\n"), Chunk("Three", "  ")),
            chunks,
        )
    }

    @Test fun longSentenceIsSplitOnWhitespace() {
        val text = "word ".repeat(300).trim() + "."
        val chunks = SentenceChunker.split(text, maxChars = 100)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.body.length <= 100 })
        assertEquals(text, SentenceChunker.join(chunks, chunks.map { it.body }))
    }

    @Test fun singleLongWordIsLeftWhole() {
        val word = "x".repeat(50)
        assertEquals(listOf(Chunk(word, "")), SentenceChunker.split(word, maxChars = 10))
    }

    @Test fun emptyTextHasNoChunks() {
        assertEquals(emptyList(), SentenceChunker.split(""))
        assertEquals("", SentenceChunker.join(emptyList(), emptyList()))
    }

    @Test fun joinNeverIntroducesDelimiters() {
        val text = "One. Two.\nThree"
        val chunks = SentenceChunker.split(text)
        val joined = SentenceChunker.join(chunks, listOf("A", "B", "C"))
        assertEquals("A B\nC", joined)
        assertTrue("^~^" !in joined)
    }

    @Test fun chunkToStringHasNoText() {
        assertTrue("secret" !in Chunk("secret", " ").toString())
    }
}
