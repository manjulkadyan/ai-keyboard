/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.tts

import java.text.BreakIterator
import java.util.Locale

/**
 * Splits text for `TextToSpeech.speak`, which refuses input longer than `getMaxSpeechInputLength()` (TRD §4.6).
 * Sentences (`BreakIterator`) are packed into chunks of at most `maxLen - 16` chars; a longer sentence is split
 * at word boundaries, and a single longer word hard. Nothing is dropped or rewritten: the chunks joined equal
 * the input, punctuation included.
 */
object TtsChunker {
    /** Head room below the engine limit. */
    const val MARGIN = 16

    /** Sentence ends `java.text.BreakIterator` may miss: Urdu full stop, Devanagari danda/double danda, Arabic ?. */
    private val EXTRA_TERMINATORS = setOf('۔', '।', '॥', '؟')

    fun chunks(text: String, locale: Locale, maxLen: Int): List<String> {
        if (text.isBlank()) return emptyList()
        val limit = (maxLen - MARGIN).coerceAtLeast(1)
        val out = mutableListOf<String>()
        val current = StringBuilder()
        for (sentence in sentences(text, locale)) {
            if (current.length + sentence.length <= limit) {
                current.append(sentence)
                continue
            }
            if (current.isNotEmpty()) {
                out += current.toString()
                current.clear()
            }
            if (sentence.length <= limit) {
                current.append(sentence)
            } else {
                for (piece in splitLong(sentence, locale, limit)) {
                    if (current.length + piece.length > limit && current.isNotEmpty()) {
                        out += current.toString()
                        current.clear()
                    }
                    current.append(piece)
                }
            }
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }

    private fun sentences(text: String, locale: Locale): List<String> =
        segments(text, BreakIterator.getSentenceInstance(locale)).flatMap(::splitAtExtraTerminators)

    /** Splits after a run of [EXTRA_TERMINATORS] and the whitespace that follows it. */
    private fun splitAtExtraTerminators(s: String): List<String> {
        val parts = mutableListOf<String>()
        var start = 0
        var i = 0
        while (i < s.length) {
            if (s[i] in EXTRA_TERMINATORS) {
                var end = i + 1
                while (end < s.length && s[end] in EXTRA_TERMINATORS) end++
                while (end < s.length && s[end].isWhitespace()) end++
                if (end < s.length) {
                    parts += s.substring(start, end)
                    start = end
                }
                i = end
            } else {
                i++
            }
        }
        parts += s.substring(start)
        return parts
    }

    /** Word-boundary pieces of at most [limit] chars (a longer word is cut at a code point boundary). */
    private fun splitLong(sentence: String, locale: Locale, limit: Int): List<String> =
        segments(sentence, BreakIterator.getWordInstance(locale)).flatMap { word ->
            if (word.length <= limit) listOf(word) else hardSplit(word, limit)
        }

    private fun hardSplit(word: String, limit: Int): List<String> {
        val parts = mutableListOf<String>()
        var start = 0
        while (start < word.length) {
            var end = minOf(start + limit, word.length)
            // Never cut a surrogate pair in half.
            if (end < word.length && Character.isHighSurrogate(word[end - 1]) && end - 1 > start) end--
            parts += word.substring(start, end)
            start = end
        }
        return parts
    }

    private fun segments(text: String, iterator: BreakIterator): List<String> {
        iterator.setText(text)
        val out = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            out += text.substring(start, end)
            start = end
            end = iterator.next()
        }
        return out
    }
}
