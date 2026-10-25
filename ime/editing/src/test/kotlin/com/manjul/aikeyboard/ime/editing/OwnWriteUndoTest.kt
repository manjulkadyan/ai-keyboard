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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OwnWriteUndoTest {
    private var now = 1_000L
    private val editor = FakeEditor("keep ")
    private val checker = HostSafetyChecker(editor)
    private val undo = OwnWriteUndo(editor, clock = { now })

    private fun commitHola() {
        editor.commit("hola ")
        undo.recordCommit(written = "hola ", restore = "hello")
    }

    @Test fun undoWithinTheWindowRemovesOurCommitAndReturnsTheSource() {
        commitHola()
        now += 4_900
        assertTrue(undo.available)
        assertEquals("hello", undo.undo())
        assertEquals("keep ", editor.text)
        assertFalse(undo.available)
        checker.assertSafe()
    }

    @Test fun undoAfterTheWindowDoesNothing() {
        commitHola()
        now += 5_100
        assertFalse(undo.available)
        assertNull(undo.undo())
        assertEquals("keep hola ", editor.text)
    }

    @Test fun hostTypedAfterTheCommitBlocksTheUndo() {
        commitHola()
        editor.hostType("x")
        now += 1_000
        assertNull(undo.undo())
        assertEquals("keep hola x", editor.text)
        checker.assertSafe()
    }

    @Test fun invalidateBlocksTheUndo() {
        commitHola()
        undo.invalidate()
        assertFalse(undo.available)
        assertNull(undo.undo())
        assertEquals("keep hola ", editor.text)
    }

    @Test fun undoWorksOnlyOnce() {
        commitHola()
        assertEquals("hello", undo.undo())
        assertNull(undo.undo())
        assertEquals("keep ", editor.text)
    }

    @Test fun aSelectionBlocksTheUndo() {
        commitHola()
        editor.hostMoveCursor(5, 10)
        assertNull(undo.undo())
        assertEquals("keep hola ", editor.text)
    }

    @Test fun aClockGoingBackwardsBlocksTheUndo() {
        commitHola()
        now -= 1
        assertFalse(undo.available)
        assertNull(undo.undo())
        assertEquals("keep hola ", editor.text)
    }

    @Test fun nothingRecordedMeansNothingToUndo() {
        assertFalse(undo.available)
        assertNull(undo.undo())
        assertEquals("keep ", editor.text)
    }
}
