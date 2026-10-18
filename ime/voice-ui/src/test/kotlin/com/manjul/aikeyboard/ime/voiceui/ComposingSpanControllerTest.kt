/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.voiceui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposingSpanControllerTest {
    /** A text field with a cursor and one composing region; reports selection updates like an editor would. */
    private class FakeField(initial: String = "") : TextTarget {
        val text = StringBuilder(initial)
        var cursor = initial.length
        var compStart = -1
        var compEnd = -1
        val calls = mutableListOf<String>()
        val pending = ArrayDeque<IntArray>()
        var deletes = 0

        /** Whether the last setComposing call started a new region at the cursor. */
        var lastStartedNew = false

        override fun setComposing(text: String): Boolean {
            calls += "set:$text"
            lastStartedNew = compStart < 0
            val start = if (compStart >= 0) compStart else cursor
            if (compStart >= 0) this.text.delete(compStart, compEnd)
            this.text.insert(start, text)
            compStart = start
            compEnd = start + text.length
            cursor = compEnd
            report()
            return true
        }

        override fun commit(text: String, removeSpaceBefore: Boolean): Boolean {
            calls += "commit:$text:$removeSpaceBefore"
            var at = cursor
            if (compStart >= 0) {
                this.text.delete(compStart, compEnd)
                at = compStart
            }
            compStart = -1
            compEnd = -1
            if (removeSpaceBefore && at > 0 && this.text[at - 1] == ' ') {
                this.text.deleteCharAt(at - 1)
                at--
            }
            this.text.insert(at, text)
            cursor = at + text.length
            report()
            return true
        }

        override fun finishComposing(): Boolean {
            calls += "finish"
            compStart = -1
            compEnd = -1
            report()
            return true
        }

        override fun clearComposing(expected: String): Boolean {
            calls += "clear:$expected"
            if (compStart < 0 || cursor < expected.length || text.substring(cursor - expected.length, cursor) != expected) {
                return false
            }
            text.delete(compStart, compEnd)
            cursor = compStart
            compStart = -1
            compEnd = -1
            report()
            return true
        }

        /** The host inserts [s] at [at] on its own; a region after [at] shifts with the text, as in an EditText. */
        fun hostInsert(at: Int, s: String) {
            text.insert(at, s)
            if (cursor >= at) cursor += s.length
            if (compStart >= at) {
                compStart += s.length
                compEnd += s.length
            }
            report()
        }

        /** The host edits the field on its own (types, moves the cursor, drops the region). */
        fun hostEdit(newCursor: Int, keepRegion: Boolean) {
            cursor = newCursor
            if (!keepRegion) {
                compStart = -1
                compEnd = -1
            }
            report()
        }

        private fun report() {
            pending.addLast(intArrayOf(cursor, cursor, compStart, compEnd))
        }

        fun deliver(span: ComposingSpanController) {
            while (pending.isNotEmpty()) {
                val u = pending.removeFirst()
                span.onSelectionUpdate(u[0], u[1], u[2], u[3])
            }
        }
    }

    @Test fun updateTwiceSetsTheLatestText() {
        val field = FakeField()
        val span = ComposingSpanController(field)
        span.update("hel")
        span.update("hello")
        field.deliver(span)
        assertEquals(listOf("set:hel", "set:hello"), field.calls)
        assertEquals("hello", field.text.toString())
        assertTrue(span.hasSpan)
    }

    @Test fun lateSelectionUpdatesOfOurOwnEditsKeepTheSpan() {
        val field = FakeField("ab ")
        val span = ComposingSpanController(field)
        span.update("h")
        span.update("he")
        span.update("hel")
        // All three updates arrive after the third edit.
        field.deliver(span)
        span.update("hello")
        field.deliver(span)
        assertEquals("ab hello", field.text.toString())
        assertEquals(listOf("set:h", "set:he", "set:hel", "set:hello"), field.calls)
        assertTrue(span.hasSpan)
    }

    @Test fun regionGoneDropsOnceAndTheNextPartialStartsANewSpanAtTheCursor() {
        val field = FakeField("one two")
        val span = ComposingSpanController(field)
        span.update("hel")
        field.deliver(span)
        // The host finished the region and moved the cursor to the start (candStart = -1).
        field.hostEdit(newCursor = 0, keepRegion = false)
        field.deliver(span)
        assertEquals(1, field.calls.count { it == "finish" })
        assertFalse(span.hasSpan)
        span.update("hello")
        assertTrue(field.lastStartedNew)
        assertEquals("helloone twohel", field.text.toString())
    }

    @Test fun cursorMovedInsideOurRegionDropsWithoutDeleting() {
        val field = FakeField("abc ")
        val span = ComposingSpanController(field)
        span.update("hello")
        field.deliver(span)
        field.hostEdit(newCursor = 6, keepRegion = true)
        field.deliver(span)
        assertEquals(listOf("set:hello", "finish"), field.calls)
        assertEquals("abc hello", field.text.toString())
    }

    @Test fun dropNeverDeletes() {
        val field = FakeField("keep ")
        val span = ComposingSpanController(field)
        span.update("partial")
        span.drop()
        span.drop()
        assertEquals(listOf("set:partial", "finish"), field.calls)
        assertEquals("keep partial", field.text.toString())
        assertEquals(0, field.deletes)
    }

    @Test fun commitReplacesTheSpan() {
        val field = FakeField()
        val span = ComposingSpanController(field)
        span.update("hel")
        span.commit("hello ")
        field.deliver(span)
        assertEquals("hello ", field.text.toString())
        assertFalse(span.hasSpan)
    }

    @Test fun commitStartingWithPunctuationJoinsThePreviousWord() {
        val field = FakeField()
        val span = ComposingSpanController(field)
        span.commit("hello ")
        span.commit(", ")
        field.deliver(span)
        assertEquals("hello, ", field.text.toString())
        assertEquals(listOf("commit:hello :false", "commit:, :true"), field.calls)
    }

    @Test fun punctuationDoesNotJoinAfterTheHostChangedTheField() {
        val field = FakeField()
        val span = ComposingSpanController(field)
        span.commit("hello ")
        field.deliver(span)
        span.update("x")
        field.deliver(span)
        field.hostEdit(newCursor = 0, keepRegion = false)
        field.deliver(span)
        span.commit(", ")
        assertEquals("commit:, :false", field.calls.last())
    }

    @Test fun emptyCommitDropsTheSpan() {
        val field = FakeField()
        val span = ComposingSpanController(field)
        span.update("um")
        span.commit("")
        assertEquals(listOf("set:um", "finish"), field.calls)
    }

    @Test fun discardRemovesOurOwnPartial() {
        val field = FakeField("keep ")
        val span = ComposingSpanController(field)
        span.update("hel")
        field.deliver(span)
        span.discard()
        assertEquals(listOf("set:hel", "clear:hel"), field.calls)
        assertEquals("keep ", field.text.toString())
        assertFalse(span.hasSpan)
        span.discard()
        assertEquals(2, field.calls.size)
    }

    @Test fun discardAfterTheHostTookTheRegionClearsNothing() {
        val field = FakeField("keep ")
        val span = ComposingSpanController(field)
        span.update("hel")
        field.deliver(span)
        field.hostEdit(newCursor = 0, keepRegion = false)
        field.deliver(span)
        span.discard()
        assertTrue(field.calls.none { it.startsWith("clear:") })
        assertEquals("keep hel", field.text.toString())
    }

    @Test fun hostMovingTheCursorAfterACommitBlocksThePunctuationJoin() {
        val field = FakeField("ab")
        val span = ComposingSpanController(field)
        span.commit("hello ")
        field.deliver(span)
        // No composing region either way: only the cursor position tells the host moved it.
        field.hostEdit(newCursor = 1, keepRegion = false)
        field.deliver(span)
        span.commit(", x")
        assertEquals("commit:, x:false", field.calls.last())
        assertEquals("a, xbhello ", field.text.toString())
    }

    @Test fun repeatedReportsOfOurCommitKeepThePunctuationJoin() {
        val field = FakeField()
        val span = ComposingSpanController(field)
        span.commit("hello ")
        field.deliver(span)
        field.hostEdit(newCursor = 6, keepRegion = false)
        field.deliver(span)
        span.commit(", ")
        assertEquals("hello, ", field.text.toString())
    }

    @Test fun twoCommitsReportedLateStillJoin() {
        val field = FakeField()
        val span = ComposingSpanController(field)
        span.commit("hello ")
        span.commit("world ")
        field.deliver(span)
        span.commit(". ")
        assertEquals("hello world. ", field.text.toString())
    }

    @Test fun newSessionNeverJoinsTheLastCommitOfTheOldOne() {
        val field = FakeField()
        val span = ComposingSpanController(field)
        span.commit("hello ")
        field.deliver(span)
        span.startSession()
        span.commit(", x")
        assertEquals("commit:, x:false", field.calls.last())
    }

    @Test fun regionShiftedIntactByTheHostIsReanchored() {
        val field = FakeField("abc ")
        val span = ComposingSpanController(field)
        span.update("hel")
        field.deliver(span)
        field.hostInsert(0, "X")
        field.deliver(span)
        assertTrue(span.hasSpan)
        span.update("hello")
        field.deliver(span)
        assertEquals("Xabc hello", field.text.toString())
        assertTrue(field.calls.none { it == "finish" })
        // Still ours after the move: a cursor move away from it drops it.
        field.hostEdit(newCursor = 1, keepRegion = true)
        field.deliver(span)
        assertEquals("finish", field.calls.last())
    }
}
