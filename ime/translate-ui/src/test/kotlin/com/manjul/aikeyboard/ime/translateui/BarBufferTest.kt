/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.translateui

import com.manjul.aikeyboard.ime.editing.testing.FakeEditor
import com.manjul.aikeyboard.ime.editing.testing.HostSafetyChecker
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Robolectric: BarBuffer uses the platform ICU break iterator (android.icu), as on a device.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BarBufferTest {
    private val buffer = BarBuffer()
    private val text get() = buffer.text.value

    // --- BarBuffer ---

    @Test
    fun `backspace removes one grapheme of a Devanagari word`() {
        buffer.type("नमस्ते")
        buffer.backspace()
        // The vowel sign never goes alone. ICU with Unicode 15.1 conjunct rules (this Robolectric SDK) treats the
        // conjunct "स्ते" as one grapheme; older device ICU removes "ते" and leaves "नमस्".
        assertTrue(text == "नम" || text == "नमस्", "unexpected length ${text.length}")
        buffer.replace("नम")
        buffer.backspace()
        assertEquals("न", text)
    }

    @Test
    fun `backspace removes a whole emoji ZWJ sequence`() {
        val family = "👨‍👩‍👧" // man ZWJ woman ZWJ girl
        val thumbsUpTone = "👍🏽" // thumbs up + skin tone modifier
        buffer.type("hi$thumbsUpTone$family")
        buffer.backspace()
        assertEquals("hi$thumbsUpTone", text)
        buffer.backspace()
        assertEquals("hi", text)
    }

    @Test
    fun `backspace on an empty buffer does nothing`() {
        buffer.backspace()
        assertEquals("", text)
    }

    @Test
    fun `the 5001st char is refused`() {
        buffer.type("a".repeat(BarBuffer.MAX_CHARS))
        buffer.type("b")
        assertEquals(BarBuffer.MAX_CHARS, text.length)
        assertFalse(text.endsWith("b"))
    }

    @Test
    fun `a key that would pass the limit is refused whole`() {
        buffer.type("a".repeat(BarBuffer.MAX_CHARS - 1))
        buffer.type("ते")
        assertEquals(BarBuffer.MAX_CHARS - 1, text.length)
    }

    @Test
    fun `paste keeps what fits and cuts at a grapheme boundary`() {
        val family = "👨‍👩‍👧"
        buffer.type("a".repeat(BarBuffer.MAX_CHARS - 3))
        buffer.paste("x$family")
        // The family emoji (8 chars) does not fit after "x"; none of its code points is kept.
        assertEquals("a".repeat(BarBuffer.MAX_CHARS - 3) + "x", text)
    }

    @Test
    fun `replace cuts an over-long text to the limit`() {
        buffer.replace("a".repeat(BarBuffer.MAX_CHARS + 10))
        assertEquals(BarBuffer.MAX_CHARS, text.length)
    }

    @Test
    fun `deleteWord removes trailing spaces and the last word`() {
        buffer.type("hello big world  ")
        buffer.deleteWord()
        assertEquals("hello big ", text)
        buffer.deleteWord()
        assertEquals("hello ", text)
        buffer.deleteWord()
        assertEquals("", text)
        buffer.deleteWord()
        assertEquals("", text)
    }

    @Test
    fun `clear affects the buffer only`() {
        val host = FakeEditor("Dear Ana, ")
        val checker = HostSafetyChecker(host)
        val target = BarBufferTarget(buffer)
        target.setComposing("hola")
        buffer.type(" amigo")

        buffer.clear()

        assertEquals("", text)
        assertEquals("Dear Ana, ", host.text)
        checker.assertSafe()
        // The target's old region is gone with the text; the next partial starts a new one.
        target.setComposing("adiós")
        assertEquals("adiós", text)
    }

    @Test
    fun `toString never contains the text`() {
        buffer.type("secret")
        assertFalse("secret" in buffer.toString())
    }

    // --- BarBufferTarget (TextTarget contract) ---

    private val target = BarBufferTarget(buffer)

    @Test
    fun `setComposing replaces our own region`() {
        buffer.type("I said ")
        assertTrue(target.setComposing("hel"))
        assertTrue(target.setComposing("hello"))
        assertEquals("I said hello", text)
    }

    @Test
    fun `a key typed after the region ends it and the next partial starts a new one`() {
        target.setComposing("hello")
        buffer.type("!")
        target.setComposing("bye")
        assertEquals("hello!bye", text)
    }

    @Test
    fun `commit replaces the region and finishes it`() {
        target.setComposing("helo")
        assertTrue(target.commit("hello "))
        assertEquals("hello ", text)
        target.setComposing("world")
        assertEquals("hello world", text)
    }

    @Test
    fun `commit with removeSpaceBefore drops one space before the insertion point`() {
        target.commit("hello ")
        target.setComposing(".")
        target.commit(".", removeSpaceBefore = true)
        assertEquals("hello.", text)
    }

    @Test
    fun `commit without a region appends`() {
        buffer.type("a ")
        target.commit("b")
        assertEquals("a b", text)
    }

    @Test
    fun `finishComposing keeps the text`() {
        target.setComposing("hello")
        assertTrue(target.finishComposing())
        assertEquals("hello", text)
        target.setComposing("x")
        assertEquals("hellox", text)
    }

    @Test
    fun `clearComposing removes the region only while it is ours and matches`() {
        buffer.type("keep ")
        target.setComposing("hello")
        assertTrue(target.clearComposing("hello"))
        assertEquals("keep ", text)

        target.setComposing("hello")
        assertFalse(target.clearComposing("other"))
        assertEquals("keep hello", text)
    }

    @Test
    fun `clearComposing after a key typed past the region removes nothing`() {
        target.setComposing("hello")
        buffer.type("!")
        assertFalse(target.clearComposing("hello"))
        assertEquals("hello!", text)
    }

    @Test
    fun `deleteBeforeCursorIfEquals removes only a matching tail`() {
        buffer.type("keep hola")
        assertFalse(target.deleteBeforeCursorIfEquals("adiós"))
        assertEquals("keep hola", text)
        assertFalse(target.deleteBeforeCursorIfEquals(""))
        assertEquals("keep hola", text)
        assertTrue(target.deleteBeforeCursorIfEquals("hola"))
        assertEquals("keep ", text)
    }

    @Test
    fun `composing past the limit is cut and stays ours`() {
        buffer.type("a".repeat(BarBuffer.MAX_CHARS - 2))
        target.setComposing("xyz")
        assertEquals(BarBuffer.MAX_CHARS, text.length)
        target.setComposing("q")
        assertEquals("a".repeat(BarBuffer.MAX_CHARS - 2) + "q", text)
    }
}
