/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.editing

import com.manjul.aikeyboard.ime.editing.testing.FakeEditor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DictatedSpanRecorderTest {
    @Test fun theCommittedTextIsTheSpanWithoutItsTrailingSpace() {
        val editor = FakeEditor("Note: ")
        editor.hostMoveCursor(6)
        val recorder = DictatedSpanRecorder { editor }
        recorder.begin()
        editor.commit("um so ", false)
        editor.commit("hello there ", false)
        val span = assertNotNull(recorder.finish("um so hello there ", "en", endedAt = 7L))
        assertEquals(6 until 23, span.range)
        assertEquals("um so hello there", span.text)
        assertEquals("en", span.lang)
        assertEquals(7L, span.endedAt)
        assertEquals("um so hello there", editor.text.substring(span.range.first, span.range.last + 1))
    }

    @Test fun joinedPunctuationStillMatches() {
        val editor = FakeEditor("")
        val recorder = DictatedSpanRecorder { editor }
        recorder.begin()
        editor.commit("hello ", false)
        editor.commit(", there ", true)
        val span = assertNotNull(recorder.finish("hello , there ", "en", 0L))
        assertEquals("hello, there", span.text)
    }

    @Test fun textTypedDuringTheDictationGivesNoSpan() {
        val editor = FakeEditor("")
        val recorder = DictatedSpanRecorder { editor }
        recorder.begin()
        editor.commit("hello ", false)
        editor.hostType("xyz ")
        assertNull(recorder.finish("hello ", "en", 0L))
    }

    @Test fun nothingCommittedOrNoFieldGivesNoSpan() {
        val editor = FakeEditor("abc")
        val recorder = DictatedSpanRecorder { editor }
        recorder.begin()
        assertNull(recorder.finish("", "en", 0L))
        assertNull(recorder.finish("   ", "en", 0L))
        val none = DictatedSpanRecorder { null }
        none.begin()
        assertNull(none.finish("hi ", "en", 0L))
    }

    @Test fun finishWithoutBeginGivesNoSpanAndASpanIsReportedOnce() {
        val editor = FakeEditor("")
        val recorder = DictatedSpanRecorder { editor }
        editor.commit("hi ", false)
        assertNull(recorder.finish("hi ", "en", 0L))
        recorder.begin()
        editor.commit("there ", false)
        assertNotNull(recorder.finish("there ", "en", 0L))
        assertNull(recorder.finish("there ", "en", 0L))
    }

    @Test fun toStringHoldsNoText() {
        val span = DictatedSpan(0 until 5, "hello", "en", 0L)
        assertEquals(false, span.toString().contains("hello"))
    }
}
