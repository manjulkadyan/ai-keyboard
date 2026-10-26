/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.editing

/**
 * What an AI rewrite works on (plan decision 10):
 * 1. the selection, if any (whole, even past [capture]'s `maxChars`, so the caller can say it is too long);
 * 2. otherwise the whole field when it has at most `maxChars` characters;
 * 3. otherwise the last at most `maxChars` characters before the cursor, starting at the earliest word or paragraph
 *    start in that window (a window that is one long word is taken as it is, never splitting a surrogate pair).
 *
 * Empty or blank text gives null. Field text is user content and is never logged.
 */
object TextCapture {
    const val DEFAULT_MAX_CHARS = 2_000

    fun capture(target: TextTarget, maxChars: Int = DEFAULT_MAX_CHARS): CapturedText? {
        require(maxChars > 0)
        val selection = target.selection()
        if (selection != null && !selection.isEmpty()) {
            val text = target.textInRange(selection) ?: return null
            return if (text.isBlank()) null else CapturedText.of(selection, text, CapturedText.Kind.SELECTION)
        }
        val snapshot = target.fieldText(maxChars) ?: return null
        if (snapshot.startOffset == 0 && snapshot.text.length <= maxChars) {
            val text = snapshot.text
            return if (text.isBlank()) null else CapturedText.of(text.indices, text, CapturedText.Kind.FIELD)
        }
        val cursor = (selection ?: snapshot.selection)?.first ?: return null
        val end = cursor - snapshot.startOffset
        val text = snapshot.text
        if (end !in 0..text.length) return null
        val windowStart = maxOf(0, end - maxChars)
        val start = boundary(text, windowStart, end, atFieldStart = snapshot.startOffset == 0)
        val captured = text.substring(start, end)
        if (captured.isBlank()) return null
        val from = snapshot.startOffset + start
        return CapturedText.of(from until from + captured.length, captured, CapturedText.Kind.BEFORE_CURSOR)
    }

    /** A known span (the dictated text, recorded by voice typing when the dictation ended). */
    fun ofSpan(range: IntRange, text: String): CapturedText {
        require(text.isNotEmpty())
        return CapturedText.of(range, text, CapturedText.Kind.DICTATED)
    }

    /** The earliest word start in `[from, to)` of [text]; index 0 counts only when it is the field start. */
    private fun boundary(text: String, from: Int, to: Int, atFieldStart: Boolean): Int {
        for (p in from until to) {
            if (text[p].isWhitespace()) continue
            if (p == 0) {
                if (atFieldStart) return 0
            } else if (text[p - 1].isWhitespace()) {
                return p
            }
        }
        return if (from > 0 && from < to && text[from].isLowSurrogate()) from + 1 else from
    }
}
