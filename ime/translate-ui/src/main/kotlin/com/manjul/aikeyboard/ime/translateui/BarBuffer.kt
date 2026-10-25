/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.translateui

import android.icu.text.BreakIterator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The text the user types into the Translate bar while it captures the keys. The cursor is always at the end.
 * Backspace removes one grapheme (a conjunct-aware Devanagari cluster, a whole emoji ZWJ sequence), and the text
 * never grows past [maxChars]. The text is user content: never logged. It never touches the host field: [clear]
 * empties this buffer only.
 *
 * Uses the ICU break iterator (`android.icu`), not `java.text`: the JVM one before JDK 20 splits emoji ZWJ
 * sequences, and tests run on the host JDK.
 */
class BarBuffer(private val maxChars: Int = MAX_CHARS) {
    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    /** Appends [s] (a key, a suggestion, a glide word); refused whole if it would pass [maxChars]. */
    fun type(s: String) {
        _text.update { current -> if (s.isEmpty() || current.length + s.length > maxChars) current else current + s }
    }

    /** Removes the last grapheme. */
    fun backspace() {
        _text.update { current ->
            if (current.isEmpty()) current else current.substring(0, characters(current).preceding(current.length))
        }
    }

    /** Removes trailing whitespace, then the last word. */
    fun deleteWord() {
        _text.update { current ->
            var end = current.length
            while (end > 0 && current[end - 1].isWhitespace()) end--
            while (end > 0 && !current[end - 1].isWhitespace()) end--
            current.substring(0, end)
        }
    }

    /** Appends as much of [s] as fits, cut at a grapheme boundary. */
    fun paste(s: String) {
        _text.update { current -> fit(current + s) }
    }

    /** Replaces the whole text (undo restore, voice partials), cut at a grapheme boundary if it is too long. */
    fun replace(s: String) {
        _text.value = fit(s)
    }

    fun clear() {
        _text.value = ""
    }

    private fun fit(s: String): String {
        if (s.length <= maxChars) return s
        val it = characters(s)
        return s.substring(0, if (it.isBoundary(maxChars)) maxChars else it.preceding(maxChars))
    }

    private fun characters(s: String): BreakIterator = BreakIterator.getCharacterInstance().apply { setText(s) }

    override fun toString(): String = "BarBuffer(chars=${_text.value.length})"

    companion object {
        /** PRD FR-13: the bar takes at most 5,000 characters. */
        const val MAX_CHARS = 5_000
    }
}
