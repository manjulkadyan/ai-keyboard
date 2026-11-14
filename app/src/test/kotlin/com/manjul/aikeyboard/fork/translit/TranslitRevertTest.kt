/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translit

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import com.manjul.aikeyboard.core.translit.Source
import com.manjul.aikeyboard.core.translit.TranslitLang
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

/**
 * The host backspace restore after a Roman space commit (decision 4), over a real [ImeTextTarget] on an [EditText]
 * connection. `KeyboardManager` itself needs the running IME service (native libraries, preferences), so these tests
 * drive the same calls its S6 hook makes: [TranslitRevert.onSpace] after the space, [TranslitRevert.undo] on
 * backspace, and the normal one-character delete when undo returns null.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TranslitRevertTest {
    private class Rig(initial: String = "") {
        val edit = EditText(RuntimeEnvironment.getApplication()).apply {
            setText(initial)
            setSelection(initial.length)
        }
        var ic: InputConnection? = edit.onCreateInputConnection(EditorInfo())
        var now = 0L
        val revert = TranslitRevert(ImeTextTarget({ ic }, { false }, {}), { ic }, { now })
        val host get() = edit.text.toString()

        /** What the host path does on space: commit the native word, then the space. */
        fun spaceCommit(c: TranslitCandidate?, typedBefore: String = "") {
            val connection = ic!!
            if (c != null) {
                connection.deleteSurroundingText(typedBefore.length, 0)
                connection.commitText(c.text, 1)
            }
            connection.commitText(" ", 1)
            revert.onSpace(c, spaceCommitted = true)
        }

        /** The backspace hook: the restore, else the normal one-character delete. */
        fun backspace() {
            if (revert.undo() == null) ic!!.deleteSurroundingText(1, 0)
        }
    }

    private fun native(text: String = "کیا", typed: String = "kya") =
        TranslitCandidate(text, 1.0, true, null, typed, TranslitLang.UR, Source.LEXICON, latin = false)

    @Test fun backspaceAfterASpaceCommitRestoresTheRomanWordOnce() {
        val r = Rig("hello kya")
        r.spaceCommit(native(), typedBefore = "kya")
        assertEquals("hello کیا ", r.host)
        r.backspace()
        assertEquals("hello kya", r.host)
        r.backspace() // The second backspace deletes one character.
        assertEquals("hello ky", r.host)
    }

    @Test fun undoReturnsTheRevertedCandidateForTheUnboost() {
        val r = Rig("kya")
        val c = native()
        r.spaceCommit(c, typedBefore = "kya")
        assertSame(c, r.revert.undo())
        assertNull(r.revert.undo())
    }

    @Test fun theLatinChipAndAPlainSpaceRecordNothing() {
        val r = Rig("kya")
        val chip = TranslitCandidate("kya", 0.0, false, null, "kya", TranslitLang.UR, Source.RULES, latin = true)
        r.spaceCommit(chip, typedBefore = "kya")
        assertFalse(r.revert.available)
        r.spaceCommit(null)
        assertFalse(r.revert.available)
        r.backspace()
        assertEquals("kya ", r.host)
    }

    @Test fun nothingHappensWhenTheTextBeforeTheCursorChanged() {
        val r = Rig("kya")
        r.spaceCommit(native(), typedBefore = "kya")
        r.ic!!.commitText("x", 1) // The host or the app wrote after our space.
        r.backspace()
        assertEquals("کیا ", r.host)
    }

    @Test fun anotherKeyEndsTheUndo() {
        val r = Rig("kya")
        r.spaceCommit(native(), typedBefore = "kya")
        r.revert.invalidate()
        r.backspace()
        assertEquals("کیا", r.host)
    }

    @Test fun aNewConnectionOrALateBackspaceDoesNothing() {
        val r = Rig("kya")
        r.spaceCommit(native(), typedBefore = "kya")
        r.now += 6_000
        r.backspace()
        assertEquals("کیا", r.host)

        val s = Rig("kya")
        s.spaceCommit(native(), typedBefore = "kya")
        s.ic = s.edit.onCreateInputConnection(EditorInfo())
        s.backspace()
        assertEquals("کیا", s.host)
    }

    @Test fun toStringHoldsNoText() {
        val r = Rig("kya")
        r.spaceCommit(native(), typedBefore = "kya")
        val s = r.revert.toString()
        assertFalse("kya" in s)
        assertFalse("کیا" in s)
    }
}
