/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.voice

import android.view.inputmethod.InputConnection
import com.manjul.aikeyboard.ime.voiceui.TextTarget

/**
 * [TextTarget] over the IME's current [InputConnection]. Each edit is one batch edit, so the host reports one
 * selection update for it. While a region is ours, [setOwnsComposing] tells the editor not to move the composing
 * region on its own (it would otherwise re-derive one from the last word).
 *
 * Never deletes text other than the single space the caller asks to join punctuation with and our own verified region
 * ([clearComposing]), and never logs text.
 */
class ImeTextTarget(
    private val connection: () -> InputConnection?,
    private val isRawInputEditor: () -> Boolean,
    private val setOwnsComposing: (Boolean) -> Unit,
) : TextTarget {
    private var owned = false

    override fun setComposing(text: String): Boolean {
        if (isRawInputEditor()) return false
        val ic = connection() ?: return false
        ic.beginBatchEdit()
        try {
            // Someone else's region (the editor's current word) stays as typed: start ours at the cursor.
            if (!owned) ic.finishComposingText()
            own(true)
            return ic.setComposingText(text, 1)
        } finally {
            ic.endBatchEdit()
        }
    }

    override fun commit(text: String, removeSpaceBefore: Boolean): Boolean {
        val ic = connection() ?: return false
        ic.beginBatchEdit()
        try {
            if (owned) ic.setComposingText("", 1)
            ic.finishComposingText()
            own(false)
            if (removeSpaceBefore && ic.getTextBeforeCursor(1, 0)?.toString() == " ") ic.deleteSurroundingText(1, 0)
            return ic.commitText(text, 1)
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
        own(false)
        // In memory only: the field text is never logged.
        if (ic.getTextBeforeCursor(expected.length, 0)?.toString() != expected) {
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

    private fun own(value: Boolean) {
        if (owned == value) return
        owned = value
        setOwnsComposing(value)
    }
}
