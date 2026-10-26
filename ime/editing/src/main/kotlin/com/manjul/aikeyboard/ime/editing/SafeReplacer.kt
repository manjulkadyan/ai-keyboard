/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.editing

enum class ReplaceResult {
    /** The range still held the captured text and now holds the output. */
    REPLACED,

    /** The range no longer holds the captured text (or cannot be read): nothing was written; offer Insert. */
    TEXT_CHANGED,

    /** Nothing was written (empty output, or the editor refused the write). */
    FAILED,
}

/**
 * Writes AI output into the host (TRD §4.1.3, Review Focus 1). [replace] re-reads the captured range and writes only
 * while it still hashes to the captured SHA-256, in one batch edit that checks the text once more. [insert] adds text
 * at the cursor and never deletes. [undo], within [undoWindowMs] of a replace, puts the original back, but only while
 * the range `[start, start + output.length)` still holds our output and the whole field is exactly as our replace
 * left it: any host edit since (even one that shifts our output or leaves an equal string in its place) blocks it.
 * Never deletes by stored length.
 *
 * Main thread only. The text is user content and is never logged.
 */
class SafeReplacer(
    private val target: TextTarget,
    private val clock: () -> Long,
    private val undoWindowMs: Long = 5_000,
) {
    private class Done(val start: Int, val output: String, val original: String, val at: Long, val field: String)

    private var done: Done? = null

    fun replace(captured: CapturedText, output: String): ReplaceResult {
        if (output.isEmpty()) return ReplaceResult.FAILED
        if (!holds(captured)) return ReplaceResult.TEXT_CHANGED
        if (!target.replaceRange(captured.range, output, expected = captured.text)) {
            return if (holds(captured)) ReplaceResult.FAILED else ReplaceResult.TEXT_CHANGED
        }
        // Without a whole-field hash there is no undo: the range alone cannot show the host left the field alone.
        done = fieldHash()?.let { Done(captured.range.first, output, captured.text, clock(), it) }
        return ReplaceResult.REPLACED
    }

    /** Inserts [output] at the cursor (after the selection, which stays). Ends any pending undo. */
    fun insert(output: String): Boolean {
        invalidate()
        if (output.isEmpty()) return false
        val selection = target.selection() ?: return false
        val at = selection.last + 1
        return target.replaceRange(at until at, output, expected = "")
    }

    /** Restores the text our last replace overwrote. Works at most once; false when it wrote nothing. */
    fun undo(): Boolean {
        val last = done ?: return false
        invalidate()
        if (clock() - last.at !in 0..undoWindowMs) return false
        val range = last.start until last.start + last.output.length
        if (target.textInRange(range) != last.output || fieldHash() != last.field) return false
        return target.replaceRange(range, last.original, expected = last.output)
    }

    /** Forgets the last replace (field changed, sheet closed): a later [undo] does nothing. */
    fun invalidate() {
        done = null
    }

    private fun fieldHash(): String? {
        val field = target.fieldText(Int.MAX_VALUE) ?: return null
        return if (field.startOffset == 0) CapturedText.sha256(field.text) else null
    }

    private fun holds(captured: CapturedText): Boolean {
        val current = target.textInRange(captured.range) ?: return false
        return CapturedText.sha256(current) == captured.sha256
    }
}
