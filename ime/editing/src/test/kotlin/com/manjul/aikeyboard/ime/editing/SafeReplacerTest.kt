/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.editing

import com.manjul.aikeyboard.ime.editing.testing.FakeEditor
import com.manjul.aikeyboard.ime.editing.testing.HostSafetyChecker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SafeReplacerTest {
    private var now = 1_000L

    private fun replacer(editor: FakeEditor) = SafeReplacer(editor, clock = { now })

    private fun selectBrave(): Pair<FakeEditor, CapturedText> {
        val editor = FakeEditor("hello brave world")
        editor.hostMoveCursor(6, 11)
        val captured = TextCapture.capture(editor)!!
        assertEquals(CapturedText.Kind.SELECTION, captured.kind)
        assertEquals("brave", captured.text)
        return editor to captured
    }

    @Test fun replacingASelectionWorks() {
        val (editor, captured) = selectBrave()
        assertEquals(ReplaceResult.REPLACED, replacer(editor).replace(captured, "bold"))
        assertEquals("hello bold world", editor.text)
        assertEquals(10, editor.selectionStart)
        assertEquals(10, editor.selectionEnd)
    }

    @Test fun replacingTheWholeFieldWorks() {
        val editor = FakeEditor("teh cat sat")
        editor.hostMoveCursor(4)
        val captured = TextCapture.capture(editor)!!
        assertEquals(CapturedText.Kind.FIELD, captured.kind)
        assertEquals(ReplaceResult.REPLACED, replacer(editor).replace(captured, "The cat sat."))
        assertEquals("The cat sat.", editor.text)
        assertEquals(12, editor.selectionStart)
    }

    @Test fun aHostEditInsideTheRangeBlocksTheReplace() {
        val (editor, captured) = selectBrave()
        editor.hostReplace(7, 8, "R")
        val before = editor.text
        assertEquals(ReplaceResult.TEXT_CHANGED, replacer(editor).replace(captured, "bold"))
        assertEquals(before, editor.text)
    }

    @Test fun aHostEditThatShiftsTheRangeBlocksTheReplace() {
        val (editor, captured) = selectBrave()
        editor.hostReplace(0, 0, "oh ")
        val checker = HostSafetyChecker(editor)
        assertEquals(ReplaceResult.TEXT_CHANGED, replacer(editor).replace(captured, "bold"))
        assertEquals("oh hello brave world", editor.text)
        checker.assertSafe()
    }

    @Test fun aHostEditOutsideTheRangeStillReplaces() {
        val (editor, captured) = selectBrave()
        editor.hostReplace(12, 17, "earth")
        assertEquals(ReplaceResult.REPLACED, replacer(editor).replace(captured, "bold"))
        assertEquals("hello bold earth", editor.text)
    }

    @Test fun aReplaceInsideTheComposingRegionLeavesNoRegionBehind() {
        val editor = FakeEditor("hello brave world")
        editor.hostMoveCursor(17)
        editor.setComposing("!")
        val captured = TextCapture.capture(editor)!!
        assertEquals(ReplaceResult.REPLACED, replacer(editor).replace(captured, "Hi."))
        assertEquals("Hi.", editor.text)
        assertEquals(-1, editor.composingStart)
    }

    @Test fun emptyOutputWritesNothing() {
        val (editor, captured) = selectBrave()
        assertEquals(ReplaceResult.FAILED, replacer(editor).replace(captured, ""))
        assertEquals("hello brave world", editor.text)
    }

    @Test fun undoWithinTheWindowRestoresTheOriginal() {
        val (editor, captured) = selectBrave()
        val replacer = replacer(editor)
        replacer.replace(captured, "bold")
        now += 5_000
        assertTrue(replacer.undo())
        assertEquals("hello brave world", editor.text)
        assertFalse(replacer.undo(), "undo works once")
        assertEquals("hello brave world", editor.text)
    }

    @Test fun undoAfterTheWindowDoesNothing() {
        val (editor, captured) = selectBrave()
        val replacer = replacer(editor)
        replacer.replace(captured, "bold")
        now += 5_001
        assertFalse(replacer.undo())
        assertEquals("hello bold world", editor.text)
    }

    @Test fun undoAfterTheHostEditsOurOutputDoesNothing() {
        val (editor, captured) = selectBrave()
        val replacer = replacer(editor)
        replacer.replace(captured, "bold")
        editor.hostReplace(7, 8, "e")
        now += 1_000
        assertFalse(replacer.undo())
        assertEquals("hello beld world", editor.text)
    }

    @Test fun undoAfterTheHostShiftsOurOutputDoesNothing() {
        val (editor, captured) = selectBrave()
        val replacer = replacer(editor)
        replacer.replace(captured, "bold")
        editor.hostReplace(0, 0, ">")
        assertFalse(replacer.undo())
        assertEquals(">hello bold world", editor.text)
    }

    @Test fun undoAfterInvalidateDoesNothing() {
        val (editor, captured) = selectBrave()
        val replacer = replacer(editor)
        replacer.replace(captured, "bold")
        replacer.invalidate()
        assertFalse(replacer.undo())
        assertEquals("hello bold world", editor.text)
    }

    @Test fun insertPutsTextAtTheCursor() {
        val editor = FakeEditor("hello world")
        editor.hostMoveCursor(5)
        assertTrue(replacer(editor).insert(" big"))
        assertEquals("hello big world", editor.text)
        assertEquals(9, editor.selectionStart)
    }

    @Test fun insertNeverDeletesTheSelection() {
        val editor = FakeEditor("hello world")
        editor.hostMoveCursor(0, 5)
        val checker = HostSafetyChecker(editor)
        assertTrue(replacer(editor).insert("X"))
        assertEquals("helloX world", editor.text)
        checker.assertSafe()
    }

    @Test fun aDictatedSpanIsReplacedWhileItIsUnchanged() {
        val editor = FakeEditor("note: ")
        editor.commit("um so lets meet at five")
        val span = TextCapture.ofSpan(6 until editor.text.length, "um so lets meet at five")
        assertEquals(CapturedText.Kind.DICTATED, span.kind)
        val replacer = replacer(editor)
        assertEquals(ReplaceResult.REPLACED, replacer.replace(span, "Let's meet at five."))
        assertEquals("note: Let's meet at five.", editor.text)
        assertTrue(replacer.undo())
        assertEquals("note: um so lets meet at five", editor.text)
    }
}
