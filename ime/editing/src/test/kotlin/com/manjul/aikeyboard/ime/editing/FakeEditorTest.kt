/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.editing

import com.manjul.aikeyboard.ime.editing.testing.FakeEditor
import com.manjul.aikeyboard.ime.editing.testing.HostSafetyChecker
import com.manjul.aikeyboard.ime.editing.testing.SelectionUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeEditorTest {
    @Test fun setComposingTextReplacesTheRegionAndPutsTheCursorAtItsEnd() {
        val editor = FakeEditor("ab")
        editor.setComposingText("he", 1)
        assertEquals("abhe", editor.text)
        assertEquals(2 to 4, editor.composingStart to editor.composingEnd)
        editor.setComposingText("hello", 1)
        assertEquals("abhello", editor.text)
        assertEquals(2 to 7, editor.composingStart to editor.composingEnd)
        assertEquals(7 to 7, editor.selectionStart to editor.selectionEnd)
    }

    @Test fun finishComposingTextKeepsTheText() {
        val editor = FakeEditor("ab")
        editor.setComposingText("he", 1)
        editor.finishComposingText()
        assertEquals("abhe", editor.text)
        assertEquals(-1 to -1, editor.composingStart to editor.composingEnd)
    }

    @Test fun commitTextReplacesTheRegionAndMovesTheCursorPastIt() {
        val editor = FakeEditor("ab cd")
        editor.hostMoveCursor(2)
        editor.setComposingText("xy", 1)
        editor.commitText("hello", 1)
        assertEquals("abhello cd", editor.text)
        assertEquals(-1, editor.composingStart)
        assertEquals(7 to 7, editor.selectionStart to editor.selectionEnd)
    }

    @Test fun commitTextWithoutARegionInsertsAtTheCursorOrReplacesTheSelection() {
        val editor = FakeEditor("abcd")
        editor.hostMoveCursor(2)
        editor.commitText("X", 1)
        assertEquals("abXcd", editor.text)
        assertEquals(3, editor.selectionStart)
        editor.hostMoveCursor(0, 2)
        editor.commitText("Y", 1)
        assertEquals("YXcd", editor.text)
        assertEquals(1 to 1, editor.selectionStart to editor.selectionEnd)
    }

    @Test fun newCursorPositionZeroLeavesTheCursorBeforeTheText() {
        val editor = FakeEditor("ab")
        editor.commitText("cd", 0)
        assertEquals("abcd", editor.text)
        assertEquals(2, editor.selectionStart)
    }

    @Test fun deleteSurroundingTextIsClampedToTheText() {
        val editor = FakeEditor("abcd")
        editor.hostMoveCursor(2)
        editor.deleteSurroundingText(1, 1)
        assertEquals("ad", editor.text)
        assertEquals(1, editor.selectionStart)
        editor.deleteSurroundingText(5, 5)
        assertEquals("", editor.text)
        assertEquals(0 to 0, editor.selectionStart to editor.selectionEnd)
    }

    @Test fun deleteSurroundingTextKeepsTheSelectedText() {
        val editor = FakeEditor("abcdef")
        editor.hostMoveCursor(2, 4)
        editor.deleteSurroundingText(1, 1)
        assertEquals("acdf", editor.text)
        assertEquals(1 to 3, editor.selectionStart to editor.selectionEnd)
    }

    @Test fun textBeforeCursorAndSelectedText() {
        val editor = FakeEditor("hello")
        assertEquals("llo", editor.getTextBeforeCursor(3))
        assertEquals("hello", editor.getTextBeforeCursor(50))
        assertNull(editor.getSelectedText())
        editor.hostMoveCursor(1, 3)
        assertEquals("h", editor.getTextBeforeCursor(3))
        assertEquals("el", editor.getSelectedText())
    }

    @Test fun hostTypingAtEitherEndOfTheRegionDoesNotGrowIt() {
        val editor = FakeEditor("ab")
        editor.setComposingText("cd", 1)
        editor.hostType("X")
        assertEquals("abcdX", editor.text)
        assertEquals(2 to 4, editor.composingStart to editor.composingEnd)
        editor.hostMoveCursor(2)
        editor.hostType("Y")
        assertEquals("abYcdX", editor.text)
        assertEquals(3 to 5, editor.composingStart to editor.composingEnd)
    }

    @Test fun hostTypingInsideTheRegionGrowsIt() {
        val editor = FakeEditor("ab")
        editor.setComposingText("cd", 1)
        editor.hostMoveCursor(3)
        editor.hostType("X")
        assertEquals("abcXd", editor.text)
        assertEquals(2 to 5, editor.composingStart to editor.composingEnd)
    }

    @Test fun deletingTheWholeRegionRemovesIt() {
        val editor = FakeEditor("ab")
        editor.setComposingText("cd", 1)
        editor.hostDelete(3)
        assertEquals("a", editor.text)
        assertEquals(-1 to -1, editor.composingStart to editor.composingEnd)
    }

    @Test fun hostReplaceAllDropsTheRegionAndPutsTheCursorAtTheEnd() {
        val editor = FakeEditor("ab")
        editor.setComposingText("cd", 1)
        editor.hostReplaceAll("xyz")
        assertEquals("xyz", editor.text)
        assertEquals(-1, editor.composingStart)
        assertEquals(3, editor.selectionStart)
    }

    @Test fun aBatchEditReportsOneSelectionUpdate() {
        val editor = FakeEditor("ab")
        editor.beginBatchEdit()
        editor.setComposingText("c", 1)
        editor.setComposingText("cd", 1)
        editor.endBatchEdit()
        assertEquals(listOf(SelectionUpdate(4, 4, 2, 4)), editor.pendingUpdates)
    }

    @Test fun anEditThatChangesNothingReportsNothing() {
        val editor = FakeEditor("ab")
        editor.finishComposingText()
        editor.hostMoveCursor(2)
        assertTrue(editor.pendingUpdates.isEmpty())
    }

    @Test fun updatesAreDeliveredInOrderAndTheNewestCanBeHeldBack() {
        val editor = FakeEditor("ab")
        val span = ComposingSpanController(editor)
        span.update("c")
        span.update("cd")
        span.update("cde")
        editor.selectionDelay = 2
        editor.deliverDue(span)
        assertEquals(2, editor.pendingUpdates.size)
        editor.deliverAll(span)
        assertTrue(editor.pendingUpdates.isEmpty())
        assertTrue(span.hasSpan)
    }

    @Test fun setComposingStartsANewRegionInsteadOfReplacingOneItDoesNotOwn() {
        val editor = FakeEditor("ab ")
        // The keyboard's own word region, set outside TextTarget.
        editor.setComposingText("wo", 1)
        assertTrue(editor.setComposing("he"))
        assertEquals("ab wohe", editor.text)
        assertEquals(5 to 7, editor.composingStart to editor.composingEnd)
        assertTrue(editor.setComposing("hello"))
        assertEquals("ab wohello", editor.text)
    }

    @Test fun deleteBeforeCursorIfEqualsOnlyRemovesMatchingText() {
        val editor = FakeEditor("keep ")
        editor.commit("hola ")
        assertFalse(editor.deleteBeforeCursorIfEquals("hello "))
        assertEquals("keep hola ", editor.text)
        assertTrue(editor.deleteBeforeCursorIfEquals("hola "))
        assertEquals("keep ", editor.text)
        assertFalse(editor.deleteBeforeCursorIfEquals("keep  "))
        assertEquals("keep ", editor.text)
    }

    @Test fun deleteBeforeCursorIfEqualsIsOneBatchEdit() {
        val editor = FakeEditor("keep hola ")
        assertTrue(editor.deleteBeforeCursorIfEquals("hola "))
        assertEquals(listOf(SelectionUpdate(5, 5, -1, -1)), editor.pendingUpdates)
    }

    @Test fun checkerSeesTheImeSideRemovingHostText() {
        val editor = FakeEditor("abc")
        val checker = HostSafetyChecker(editor)
        editor.hostDelete(1)
        editor.hostType("xy")
        checker.assertSafe()
        editor.deleteSurroundingText(1, 0)
        assertFailsWith<AssertionError> { checker.assertSafe() }
    }

    @Test fun checkerSeesTheImeSideReplacingHostTextWithTheSameCharacters() {
        val editor = FakeEditor("abc")
        val checker = HostSafetyChecker(editor)
        editor.hostMoveCursor(1, 2)
        editor.commitText("b", 1)
        assertEquals("abc", editor.text)
        assertFailsWith<AssertionError> { checker.assertSafe() }
    }
}
