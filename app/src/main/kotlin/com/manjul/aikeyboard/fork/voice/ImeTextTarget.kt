/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.voice

import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import com.manjul.aikeyboard.ime.editing.FieldSnapshot
import com.manjul.aikeyboard.ime.editing.TextTarget

/**
 * [TextTarget] over the IME's current [InputConnection]. Each edit is one batch edit, so the host reports one
 * selection update for it. While a region is ours, [setOwnsComposing] tells the editor not to move the composing
 * region on its own (it would otherwise re-derive one from the last word).
 *
 * Never deletes text other than the single space the caller asks to join punctuation with, our own verified region,
 * our own verified commit ([deleteBeforeCursorIfEquals]) and a range the caller verified ([replaceRange], for
 * `SafeReplacer`), and never logs text. Range reads use `getExtractedText` (field offsets); editors without it, and
 * raw input editors, give null and take no range writes.
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

    override fun selection(): IntRange? {
        val ic = connection() ?: return null
        return extract(ic)?.let(::selectionOf)
    }

    override fun textInRange(range: IntRange): String? {
        val ic = connection() ?: return null
        return extract(ic)?.let { read(it, range) }
    }

    override fun fieldText(maxChars: Int): FieldSnapshot? {
        val ic = connection() ?: return null
        val extracted = extract(ic) ?: return null
        val text = extracted.text?.toString() ?: return null
        val selection = selectionOf(extracted) ?: return null
        val offset = extracted.startOffset
        if (offset == 0 && text.length <= maxChars) return FieldSnapshot(text, 0, selection)
        // Keep a window around the selection: longer than maxChars or starting after 0 (the TextTarget contract).
        val selStart = selection.first - offset
        val selEnd = selection.last + 1 - offset
        val start = maxOf(0, selStart - maxChars)
        val end = minOf(text.length, maxOf(selEnd.toLong() + maxChars, start.toLong() + maxChars + 1).toInt())
        return FieldSnapshot(text.substring(start, end), offset + start, selection)
    }

    override fun replaceRange(range: IntRange, text: String, expected: String?): Boolean {
        if (isRawInputEditor()) return false
        val start = range.first
        val end = range.last + 1
        if (start < 0 || end < start) return false
        val ic = connection() ?: return false
        ic.beginBatchEdit()
        try {
            if (expected != null && extract(ic)?.let { read(it, range) } != expected) return false
            // A composing region (ours or the editor's current word) is finished, so commitText replaces the
            // selection and nothing else.
            ic.finishComposingText()
            own(false)
            if (!ic.setSelection(start, end)) return false
            return ic.commitText(text, 1)
        } finally {
            ic.endBatchEdit()
        }
    }

    private fun extract(ic: InputConnection): ExtractedText? {
        if (isRawInputEditor()) return null
        val request = ExtractedTextRequest().apply { hintMaxChars = EXTRACT_HINT_MAX_CHARS }
        return ic.getExtractedText(request, 0)
    }

    /** Selection in field offsets; editors may report it reversed. */
    private fun selectionOf(extracted: ExtractedText): IntRange? {
        val a = extracted.selectionStart
        val b = extracted.selectionEnd
        if (a < 0 || b < 0) return null
        val length = extracted.text?.length ?: return null
        if (maxOf(a, b) > length) return null
        return (extracted.startOffset + minOf(a, b)) until (extracted.startOffset + maxOf(a, b))
    }

    private fun read(extracted: ExtractedText, range: IntRange): String? {
        val text = extracted.text ?: return null
        val start = range.first - extracted.startOffset
        val end = range.last + 1 - extracted.startOffset
        if (start < 0 || end < start || end > text.length) return null
        return text.subSequence(start, end).toString()
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

        /** A hint only (editors such as TextView return the whole text anyway). */
        const val EXTRACT_HINT_MAX_CHARS = 1 shl 16
    }
}
