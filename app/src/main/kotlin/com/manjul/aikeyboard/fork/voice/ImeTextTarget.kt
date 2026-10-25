/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.voice

import android.view.inputmethod.InputConnection
import com.manjul.aikeyboard.ime.editing.TextTarget

/**
 * [TextTarget] over the IME's current [InputConnection]. Each edit is one batch edit, so the host reports one
 * selection update for it. While a region is ours, [setOwnsComposing] tells the editor not to move the composing
 * region on its own (it would otherwise re-derive one from the last word).
 *
 * Never deletes text other than the single space the caller asks to join punctuation with, our own verified region
 * and our own verified commit ([deleteBeforeCursorIfEquals]), and never logs text.
 *
 * Selection updates arrive late, so before replacing our region or joining onto our last commit this checks that the
 * cursor is still right after it: same connection, no selection, same text before and after the cursor as when we
 * wrote it. Otherwise the host may have moved the cursor or typed inside our region, and the region is only finished.
 * `FakeEditor` in the `:ime:editing` test fixtures mirrors this class; keep the two in step.
 */
class ImeTextTarget(
    private val connection: () -> InputConnection?,
    private val isRawInputEditor: () -> Boolean,
    private val setOwnsComposing: (Boolean) -> Unit,
) : TextTarget {
    private var owned = false

    /** Our composing text and our last commit, where we left them (see [isAt]). */
    private var region: Anchor? = null
    private var lastCommit: Anchor? = null

    private class Anchor(val connection: InputConnection, val text: String, val after: String?)

    override fun setComposing(text: String): Boolean {
        if (isRawInputEditor()) return false
        val ic = connection() ?: return false
        ic.beginBatchEdit()
        try {
            // Someone else's region (the editor's current word), or ours after the host moved or typed into it, stays
            // as it is: start ours at the cursor.
            if (!ownsVerifiedRegion(ic)) ic.finishComposingText()
            own(true)
            val ok = ic.setComposingText(text, 1)
            region = anchor(ic, text)
            return ok
        } finally {
            ic.endBatchEdit()
        }
    }

    override fun commit(text: String, removeSpaceBefore: Boolean): Boolean {
        val ic = connection() ?: return false
        ic.beginBatchEdit()
        try {
            if (ownsVerifiedRegion(ic)) ic.setComposingText("", 1)
            ic.finishComposingText()
            own(false)
            val joinOurSpace = removeSpaceBefore && isAt(ic, lastCommit)
            if (joinOurSpace && ic.getTextBeforeCursor(1, 0)?.toString() == " ") ic.deleteSurroundingText(1, 0)
            val ok = ic.commitText(text, 1)
            lastCommit = anchor(ic, text)
            return ok
        } finally {
            ic.endBatchEdit()
        }
    }

    override fun finishComposing(): Boolean {
        if (!owned) return true
        own(false)
        return connection()?.finishComposingText() ?: false
    }

    override fun clearComposing(expected: String): Boolean {
        if (!owned) return false
        val ic = connection() ?: return false
        val verified = ownsVerifiedRegion(ic)
        own(false)
        // In memory only: the field text is never logged.
        if (!verified || ic.getTextBeforeCursor(expected.length, 0)?.toString() != expected) {
            ic.finishComposingText()
            return false
        }
        ic.beginBatchEdit()
        try {
            ic.setComposingText("", 1)
            ic.finishComposingText()
            return true
        } finally {
            ic.endBatchEdit()
        }
    }

    override fun deleteBeforeCursorIfEquals(expected: String): Boolean {
        if (expected.isEmpty()) return false
        val ic = connection() ?: return false
        ic.beginBatchEdit()
        try {
            if (!ic.getSelectedText(0).isNullOrEmpty()) return false
            if (ic.getTextBeforeCursor(expected.length, 0)?.toString() != expected) return false
            return ic.deleteSurroundingText(expected.length, 0)
        } finally {
            ic.endBatchEdit()
        }
    }

    /** The input connection changed (new field): nothing on it is ours, so later edits never act as if it were. */
    fun forgetOwnedRegion() {
        own(false)
        lastCommit = null
    }

    private fun own(value: Boolean) {
        if (!value) region = null
        if (owned == value) return
        owned = value
        setOwnsComposing(value)
    }

    private fun ownsVerifiedRegion(ic: InputConnection): Boolean = owned && isAt(ic, region)

    private fun anchor(ic: InputConnection, text: String) =
        Anchor(ic, text, ic.getTextAfterCursor(CONTEXT, 0)?.toString())

    /** The cursor is still right after [anchor]'s text, on the same connection, with the same text after it. */
    private fun isAt(ic: InputConnection, anchor: Anchor?): Boolean =
        anchor != null && anchor.connection === ic && ic.getSelectedText(0).isNullOrEmpty() &&
            ic.getTextBeforeCursor(anchor.text.length, 0)?.toString() == anchor.text &&
            ic.getTextAfterCursor(CONTEXT, 0)?.toString() == anchor.after

    private companion object {
        /** Characters after the cursor compared by [isAt]: text the host typed inside our region shows up here. */
        const val CONTEXT = 64
    }
}
