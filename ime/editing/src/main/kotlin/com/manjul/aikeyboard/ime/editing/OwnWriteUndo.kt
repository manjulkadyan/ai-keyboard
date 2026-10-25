/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.editing

/**
 * Undo of our last commit (TRD §4.1.3): within [windowMs] it removes the text we wrote, but only if the text right
 * before the cursor still equals it ([TextTarget.deleteBeforeCursorIfEquals]), and hands back what to restore in the
 * caller's buffer. Late, invalidated (host edit, field change) or failed undos touch nothing. Main thread only; the
 * text is user content and is never logged.
 */
class OwnWriteUndo(
    private val target: TextTarget,
    private val clock: () -> Long,
    private val windowMs: Long = 5_000,
) {
    private var written: String? = null
    private var restore: String? = null
    private var at = 0L

    /** Whether [undo] may still act: something is recorded and the window has not passed. */
    val available: Boolean
        get() = written != null && clock() - at in 0..windowMs

    /** We just committed [written] to the host; [restore] goes back to the caller if the user undoes it. */
    fun recordCommit(written: String, restore: String) {
        this.written = written
        this.restore = restore
        at = clock()
    }

    /** Removes our commit and returns the text to restore, or null (nothing removed). Works at most once. */
    fun undo(): String? {
        val text = written
        val result = restore
        val ok = available
        invalidate()
        if (!ok || text == null) return null
        return if (target.deleteBeforeCursorIfEquals(text)) result else null
    }

    /** Forgets the recorded commit (host edit, field change): a later [undo] does nothing. */
    fun invalidate() {
        written = null
        restore = null
    }
}
