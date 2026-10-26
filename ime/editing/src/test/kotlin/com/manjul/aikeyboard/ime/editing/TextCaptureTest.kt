/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.editing

import com.manjul.aikeyboard.ime.editing.testing.FakeEditor
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TextCaptureTest {
    private fun end(range: IntRange) = range.last + 1

    @Test fun aSelectionWins() {
        val editor = FakeEditor("short field with a selection")
        editor.hostMoveCursor(6, 11)
        val captured = assertNotNull(TextCapture.capture(editor))
        assertEquals(CapturedText.Kind.SELECTION, captured.kind)
        assertEquals(6 until 11, captured.range)
        assertEquals("field", captured.text)
    }

    @Test fun aFieldOfAtMost2000CharsIsCapturedWhole() {
        val text = "a".repeat(1_000) + " " + "b".repeat(999)
        val editor = FakeEditor(text)
        editor.hostMoveCursor(10)
        val captured = assertNotNull(TextCapture.capture(editor))
        assertEquals(CapturedText.Kind.FIELD, captured.kind)
        assertEquals(0 until 2_000, captured.range)
        assertEquals(text, captured.text)
        assertEquals(CapturedText.sha256(text), captured.sha256)
    }

    @Test fun aLongFieldGivesTheTextBeforeTheCursorFromAWordBoundary() {
        val random = Random(7)
        val text = buildString {
            while (length < 5_000) {
                append(String(CharArray(random.nextInt(1, 12)) { 'a' + random.nextInt(26) }))
                append(if (random.nextInt(15) == 0) "\n\n" else " ")
            }
        }.take(5_000)
        val editor = FakeEditor(text)
        editor.hostMoveCursor(4_000)
        val captured = assertNotNull(TextCapture.capture(editor))
        assertEquals(CapturedText.Kind.BEFORE_CURSOR, captured.kind)
        assertEquals(4_000, end(captured.range))
        assertTrue(captured.text.length <= 2_000)
        assertTrue(captured.text.length >= 1_980, "starts at the earliest boundary in the window")
        val start = captured.range.first
        assertTrue(text[start - 1].isWhitespace() && !text[start].isWhitespace())
        assertEquals(text.substring(start, 4_000), captured.text)
    }

    @Test fun aLongFieldWithTheCursorNearTheStartTakesTheFieldStart() {
        val text = "word ".repeat(1_000)
        val editor = FakeEditor(text)
        editor.hostMoveCursor(503)
        val captured = assertNotNull(TextCapture.capture(editor))
        assertEquals(CapturedText.Kind.BEFORE_CURSOR, captured.kind)
        assertEquals(0 until 503, captured.range)
    }

    @Test fun emptyOrBlankTextGivesNull() {
        assertNull(TextCapture.capture(FakeEditor("")))
        assertNull(TextCapture.capture(FakeEditor("   \n ")))
        val long = FakeEditor("x ".repeat(3_000))
        long.hostMoveCursor(0)
        assertNull(TextCapture.capture(long))
    }

    @Test fun aSpanIsCapturedAsDictated() {
        val span = TextCapture.ofSpan(4 until 9, "hello")
        assertEquals(CapturedText.Kind.DICTATED, span.kind)
        assertEquals(CapturedText.sha256("hello"), span.sha256)
    }

    @Test fun toStringHoldsNoText() {
        val editor = FakeEditor("secret words")
        val captured = assertNotNull(TextCapture.capture(editor))
        assertFalse("secret" in captured.toString())
        assertFalse("secret" in assertNotNull(editor.fieldText(100)).toString())
    }
}
