/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.tts

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TtsChunkerTest {
    @Test
    fun `three short sentences make one chunk`() {
        val text = "Hello there. How are you? I am fine!"
        assertEquals(listOf(text), TtsChunker.chunks(text, Locale.ENGLISH, 4000))
    }

    @Test
    fun `blank text has no chunks`() {
        assertTrue(TtsChunker.chunks("  \n", Locale.ENGLISH, 4000).isEmpty())
    }

    @Test
    fun `a 10000 char text is cut under the limit without losing anything`() {
        val sentence = "The quick brown fox, it seems, jumps over the lazy dog; again and again. "
        val text = sentence.repeat(10_000 / sentence.length + 1).take(10_000)
        val maxLen = 500
        val chunks = TtsChunker.chunks(text, Locale.ENGLISH, maxLen)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= maxLen - TtsChunker.MARGIN }, "a chunk exceeds the limit")
        assertEquals(text, chunks.joinToString(""))
    }

    @Test
    fun `a sentence longer than the limit splits at words, a long word hard`() {
        val long = "word ".repeat(100) + "x".repeat(300) + "."
        val chunks = TtsChunker.chunks(long, Locale.ENGLISH, 116)
        assertTrue(chunks.all { it.length <= 100 })
        assertEquals(long, chunks.joinToString(""))
        assertTrue(chunks.first().endsWith(" "), "word pieces break after a space")
    }

    @Test
    fun `Urdu full stop and Hindi danda end sentences`() {
        val urdu = "یہ پہلا جملہ ہے۔ یہ دوسرا جملہ ہے۔ "
        val urduChunks = TtsChunker.chunks(urdu, Locale.forLanguageTag("ur"), 16 + 20)
        assertEquals(listOf("یہ پہلا جملہ ہے۔ ", "یہ دوسرا جملہ ہے۔ "), urduChunks)

        val hindi = "यह पहला वाक्य है। यह दूसरा वाक्य है।"
        val hindiChunks = TtsChunker.chunks(hindi, Locale.forLanguageTag("hi"), 16 + 20)
        assertEquals(listOf("यह पहला वाक्य है। ", "यह दूसरा वाक्य है।"), hindiChunks)
    }
}
