/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.suggest

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import com.manjul.aikeyboard.fork.voice.ImeTextTarget
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

/** DT-G.2: the first backspace after an autocorrect on space restores the typed word, once. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AutocorrectRevertTest {
    private class Rig(initial: String) {
        val edit = EditText(RuntimeEnvironment.getApplication()).apply {
            setText(initial)
            setSelection(initial.length)
        }
        var ic: InputConnection? = edit.onCreateInputConnection(EditorInfo())
        var now = 0L
        val revert = AutocorrectRevert(ImeTextTarget({ ic }, { false }, {}), { ic }, { now })
        val host get() = edit.text.toString()

        fun spaceCommit(c: LatinWordCandidate?, typedBefore: String = "") {
            val connection = ic!!
            if (c != null) {
                connection.deleteSurroundingText(typedBefore.length, 0)
                connection.commitText(c.text, 1)
            }
            connection.commitText(" ", 1)
            revert.onSpace(c, spaceCommitted = true)
        }

        fun backspace() {
            if (revert.undo() == null) ic!!.deleteSurroundingText(1, 0)
        }
    }

    private fun cand(text: String = "the", typed: String = "teh") =
        LatinWordCandidate(text, 1.0, true, null, typed, null, "en")

    @Test fun backspaceAfterAutocorrectRestoresTypedWordOnce() {
        val r = Rig("hi teh")
        r.spaceCommit(cand(), "teh")
        assertEquals("hi the ", r.host)
        r.backspace()
        assertEquals("hi teh", r.host)
        r.backspace()
        assertEquals("hi te", r.host)
    }

    @Test fun undoReturnsCandidateOnce() {
        val r = Rig("teh")
        val c = cand()
        r.spaceCommit(c, "teh")
        assertSame(c, r.revert.undo())
        assertNull(r.revert.undo())
    }

    @Test fun unchangedWordOrNoCandidateRecordsNothing() {
        val r = Rig("the")
        r.spaceCommit(cand("the", "the"), "the")
        assertFalse(r.revert.available)
        r.spaceCommit(null)
        r.backspace()
        assertEquals("the ", r.host)
    }

    @Test fun editedTextOrExpiredWindowDoesNothing() {
        val r = Rig("teh")
        r.spaceCommit(cand(), "teh")
        r.ic!!.commitText("x", 1)
        assertNull(r.revert.undo())
        val r2 = Rig("teh")
        r2.spaceCommit(cand(), "teh")
        r2.now = 6_000
        assertNull(r2.revert.undo())
        assertEquals("the ", r2.host)
    }
}
