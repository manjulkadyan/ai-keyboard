/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.editing

/**
 * Keeps the dictation partial in a composing region and lets it go as soon as the host field moves away from what
 * we wrote. Main thread only.
 *
 * Selection updates arrive late: one for each of our edits, possibly after we already made the next one. Every edit
 * queues the composing length it will report (-1 for none); an update matching a queued entry is ours. Anything
 * else (cursor moved, host edited the text, region gone) drops the span, so the next partial starts a new one at the
 * cursor instead of overwriting text that is not ours. A region the host only shifted (same length, cursor still at its
 * end, e.g. text inserted before it) is still ours and stays.
 */
class ComposingSpanController(private val target: TextTarget) {
    private class Expect(val span: Int, val length: Int, val commit: Int = 0)

    private var span: String? = null
    private var spanId = 0
    private var commitId = 0
    private val expected = ArrayDeque<Expect>()

    /** The last commit ended with a space and the host has not changed the field since. */
    private var trailingSpace = false

    /** Cursor reported for the last commit (-1 until its update arrives); any other cursor means the host moved it. */
    private var commitCursor = -1

    val hasSpan: Boolean get() = span != null

    fun update(text: String) {
        if (span == text) return
        if (span == null) spanId++
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
        commitId++
        commitCursor = -1
        expect(-1, commit = commitId)
        trailingSpace = text.endsWith(' ')
    }

    /** A new dictation: never join punctuation to the previous one's last word. */
    fun startSession() {
        trailingSpace = false
    }

    /** Finishes the composing region and forgets it. Never deletes. */
    fun drop() {
        trailingSpace = false
        if (span == null) return
        span = null
        target.finishComposing()
        expect(-1)
    }

    /**
     * Removes our partial from the field (the session was cancelled, and the field may now be sensitive). Only our
     * verified region is removed (see [TextTarget.clearComposing]); a span we already let go of stays as it is.
     */
    fun discard() {
        trailingSpace = false
        val text = span ?: return
        span = null
        target.clearComposing(text)
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
        if (match.commit != 0 && match.commit == commitId) {
            // No region either way, so the cursor is the only sign the host moved it.
            if (commitCursor < 0) commitCursor = newSelStart else if (newSelStart != commitCursor) trailingSpace = false
        }
        if (span == null || length < 0 || match.span != spanId) return
        // Same length (matched above) and the cursor at its end: ours, even if the host shifted where it starts.
        if (newSelStart != candEnd || newSelEnd != candEnd) hostChanged()
    }

    private fun hostChanged() {
        expected.clear()
        drop()
    }

    private fun expect(length: Int, commit: Int = 0) {
        if (expected.size >= MAX_EXPECTED) expected.removeFirst()
        expected.addLast(Expect(if (length < 0) -1 else spanId, length, commit))
    }

    private companion object {
        /** A commit starting with one of these joins the previous word: "hello " + ", " gives "hello, ". */
        const val LEADING_PUNCTUATION = ",.?!،؟।"
        const val MAX_EXPECTED = 32
    }
}
