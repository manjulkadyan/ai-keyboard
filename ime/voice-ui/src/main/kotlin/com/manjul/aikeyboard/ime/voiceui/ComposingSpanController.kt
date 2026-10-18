/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.voiceui

/** The host text field, seen by voice typing. The text is user content: implementations never log it. */
interface TextTarget {
    /**
     * Shows [text] as the composing region: replaces our own region, or starts a new one at the cursor
     * (a region someone else set is finished first, never replaced). False if the field has no composing support.
     */
    fun setComposing(text: String): Boolean

    /**
     * Replaces our composing region (if any) with [text] and finishes it. With [removeSpaceBefore], a single space
     * directly before the insertion point is removed first (the trailing space of our previous commit).
     */
    fun commit(text: String, removeSpaceBefore: Boolean = false): Boolean

    /** Finishes our composing region: the text stays as it is. Never deletes. */
    fun finishComposing(): Boolean
}

/**
 * Keeps the dictation partial in a composing region and lets it go as soon as the host field moves away from what
 * we wrote. Main thread only.
 *
 * Selection updates arrive late: one for each of our edits, possibly after we already made the next one. Every edit
 * queues the composing length it will report (-1 for none); an update matching a queued entry is ours. Anything
 * else (cursor moved, host edited the text, region gone) drops the span, so the next partial starts a new one at the
 * cursor instead of overwriting text that is not ours.
 */
class ComposingSpanController(private val target: TextTarget) {
    private class Expect(val span: Int, val length: Int)

    private var span: String? = null
    private var spanId = 0
    private var anchor = -1
    private val expected = ArrayDeque<Expect>()

    /** The last commit ended with a space and the host has not changed the field since. */
    private var trailingSpace = false

    val hasSpan: Boolean get() = span != null

    fun update(text: String) {
        if (span == text) return
        if (span == null) {
            spanId++
            anchor = -1
        }
        if (!target.setComposing(text)) {
            span = null
            return
        }
        span = text
        expect(text.length)
    }

    fun commit(text: String) {
        if (text.isEmpty()) {
            drop()
            return
        }
        val joinPunctuation = trailingSpace && text.first() in LEADING_PUNCTUATION
        span = null
        target.commit(text, removeSpaceBefore = joinPunctuation)
        expect(-1)
        trailingSpace = text.endsWith(' ')
    }

    /** Finishes the composing region and forgets it. Never deletes. */
    fun drop() {
        trailingSpace = false
        if (span == null) return
        span = null
        target.finishComposing()
        expect(-1)
    }

    fun onSelectionUpdate(newSelStart: Int, newSelEnd: Int, candStart: Int, candEnd: Int) {
        val length = if (candStart < 0 || candEnd < candStart) -1 else candEnd - candStart
        val index = expected.indexOfFirst { it.length == length }
        if (index < 0) {
            hostChanged()
            return
        }
        repeat(index) { expected.removeFirst() }
        // Keep the newest entry once matched (a host may report the same state twice), but not an older one:
        // after a later edit that state cannot come back, so seeing it again means the host changed the field.
        val match = if (expected.size > 1) expected.removeFirst() else expected.first()
        if (span == null || length < 0 || match.span != spanId) return
        if (newSelStart != candEnd || newSelEnd != candEnd || (anchor >= 0 && candStart != anchor)) {
            hostChanged()
            return
        }
        anchor = candStart
    }

    private fun hostChanged() {
        expected.clear()
        drop()
    }

    private fun expect(length: Int) {
        if (expected.size >= MAX_EXPECTED) expected.removeFirst()
        expected.addLast(Expect(if (length < 0) -1 else spanId, length))
    }

    private companion object {
        /** A commit starting with one of these joins the previous word: "hello " + ", " gives "hello, ". */
        const val LEADING_PUNCTUATION = ",.?!،؟।"
        const val MAX_EXPECTED = 32
    }
}
