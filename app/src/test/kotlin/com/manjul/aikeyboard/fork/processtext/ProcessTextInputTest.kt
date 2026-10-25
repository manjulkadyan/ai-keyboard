/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.processtext

import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.text.SpannableString
import com.manjul.aikeyboard.core.analytics.ProcessTextAction
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ProcessTextInputTest {
    private fun processText(text: CharSequence?, readOnly: Boolean? = null) = Intent(Intent.ACTION_PROCESS_TEXT).apply {
        type = "text/plain"
        if (text != null) putExtra(Intent.EXTRA_PROCESS_TEXT, text)
        if (readOnly != null) putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, readOnly)
    }

    private fun send(text: CharSequence?, type: String? = "text/plain") = Intent(Intent.ACTION_SEND).apply {
        this.type = type
        if (text != null) putExtra(Intent.EXTRA_TEXT, text)
    }

    @Test
    fun editableProcessTextCanBeReplaced() {
        val input = assertNotNull(ProcessTextInput.parse(processText("hola")))
        assertEquals("hola", input.text)
        assertEquals(ProcessTextAction.PROCESS_TEXT, input.action)
        assertFalse(input.readOnly)
        assertFalse(input.truncated)
        assertTrue(input.canReplace)
    }

    @Test
    fun readOnlyProcessTextCannotBeReplaced() {
        val input = assertNotNull(ProcessTextInput.parse(processText("hola", readOnly = true)))
        assertTrue(input.readOnly)
        assertFalse(input.canReplace)
    }

    @Test
    fun spannedSelectionIsReadAsPlainText() {
        val input = assertNotNull(ProcessTextInput.parse(processText(SpannableString("bonjour"))))
        assertEquals("bonjour", input.text)
    }

    @Test
    fun sendOfPlainTextIsNeverReplaceable() {
        val input = assertNotNull(ProcessTextInput.parse(send("hello")))
        assertEquals(ProcessTextAction.SEND, input.action)
        assertFalse(input.canReplace)
    }

    @Test
    fun invalidShapesAreRejected() {
        val rejected = listOf(
            null,
            Intent(),
            Intent(Intent.ACTION_VIEW).putExtra(Intent.EXTRA_PROCESS_TEXT, "x"),
            processText(null),
            processText(""),
            processText("   \n"),
            processText("x").apply { type = "image/png" },
            processText("x").setData(Uri.parse("content://evil/1")),
            Intent(Intent.ACTION_PROCESS_TEXT).putExtra(Intent.EXTRA_PROCESS_TEXT, Rect(1, 2, 3, 4)),
            Intent(Intent.ACTION_PROCESS_TEXT).putExtra(Intent.EXTRA_PROCESS_TEXT, 42),
            send(null),
            send("x", type = null),
            send("x", type = "text/html"),
            send("x", type = "image/*"),
            Intent(Intent.ACTION_SEND).apply { type = "text/plain" }.putExtra(Intent.EXTRA_TEXT, Rect()),
            send("x").setData(Uri.parse("file:///sdcard/x")),
        )
        for ((i, intent) in rejected.withIndex()) assertNull(ProcessTextInput.parse(intent), "case $i")
    }

    @Test
    fun longTextIsTruncatedTo5000() {
        val input = assertNotNull(ProcessTextInput.parse(processText("a".repeat(6_000))))
        assertEquals(5_000, input.text.length)
        assertTrue(input.truncated)
        val exact = assertNotNull(ProcessTextInput.parse(processText("a".repeat(5_000))))
        assertFalse(exact.truncated)
    }

    @Test
    fun truncationNeverSplitsASurrogatePair() {
        val text = "a".repeat(4_999) + "😀" + "b"
        val cut = ProcessTextInput.truncate(text)
        assertEquals(4_999, cut.length)
        assertFalse(Character.isHighSurrogate(cut.last()))
    }

    @Test
    fun toStringNeverContainsTheText() {
        val input = assertNotNull(ProcessTextInput.parse(processText("secret words")))
        assertFalse(input.toString().contains("secret"))
    }
}
