/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.editing

/**
 * The text one dictation committed into the host (plan decision 11): [range] (field offsets, `start until end`) held
 * [text] when the dictation ended at [endedAt]. [lang] is the dictation language (BCP-47). Polish replaces only this
 * range, and only while it still holds [text] (see [SafeReplacer]). [text] is user content: [toString] holds no text.
 */
data class DictatedSpan(val range: IntRange, val text: String, val lang: String, val endedAt: Long) {
    init {
        require(range.last + 1 - range.first == text.length) { "range and text lengths differ" }
    }

    override fun toString(): String = "DictatedSpan(range=$range, chars=${text.length}, lang=$lang, endedAt=$endedAt)"
}

/**
 * Finds the span one dictation wrote. [begin] notes the cursor when the dictation starts; [finish] reads the field
 * from there to the cursor and gives the span only when that text is what the dictation committed (compared without
 * whitespace, since a commit may drop the space before joined punctuation). Leading and trailing whitespace is not
 * part of the span. Anything else in between (typing, a host edit, a moved cursor, a selection) gives null, so Polish
 * is never offered on text the user did not dictate.
 *
 * [target] is the field the dictation writes into, or null when it does not write into the host (the translate bar).
 * Main thread only. Field text is never logged.
 */
class DictatedSpanRecorder(private val target: () -> TextTarget?) {
    private var start: Int? = null

    fun begin() {
        start = target()?.selection()?.first
    }

    /** The span the dictation that committed [committed] wrote, or null. Forgets the start either way. */
    fun finish(committed: String, lang: String, endedAt: Long): DictatedSpan? {
        val from = start ?: return null
        start = null
        if (committed.isBlank()) return null
        val field = target() ?: return null
        val selection = field.selection() ?: return null
        if (!selection.isEmpty()) return null
        val to = selection.first
        if (to <= from) return null
        val written = field.textInRange(from until to) ?: return null
        if (written.filterNot { it.isWhitespace() } != committed.filterNot { it.isWhitespace() }) return null
        val lead = written.indexOfFirst { !it.isWhitespace() }
        if (lead < 0) return null
        val text = written.trim { it.isWhitespace() }
        val first = from + lead
        return DictatedSpan(first until first + text.length, text, lang, endedAt)
    }

    /** The dictation was cancelled: no span. */
    fun reset() {
        start = null
    }
}
