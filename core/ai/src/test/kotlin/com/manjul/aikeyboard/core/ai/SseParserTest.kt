/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SseParserTest {
    private fun parseAll(vararg chunks: String): List<SseParser.Event> {
        val parser = SseParser()
        return chunks.flatMap { parser.feed(it) }
    }

    @Test
    fun `one event with a type and data`() {
        val events = parseAll("event: delta\ndata: {\"text\":\"hi\"}\n\n")
        assertEquals(listOf(SseParser.Event("delta", "{\"text\":\"hi\"}")), events)
    }

    @Test
    fun `multi-line data is joined with newlines`() {
        val events = parseAll("event: done\ndata: line one\ndata: line two\ndata:\n\n")
        assertEquals(listOf(SseParser.Event("done", "line one\nline two\n")), events)
    }

    @Test
    fun `comments and unknown fields are ignored`() {
        val events = parseAll(": keep-alive\n\n:another\nid: 7\nretry: 100\nevent: delta\ndata: x\n\n")
        assertEquals(listOf(SseParser.Event("delta", "x")), events)
    }

    @Test
    fun `an event with no data is not dispatched and the type resets`() {
        val events = parseAll("event: delta\n\ndata: y\n\n")
        assertEquals(listOf(SseParser.Event("message", "y")), events)
    }

    @Test
    fun `CRLF and lone CR line endings`() {
        assertEquals(
            listOf(SseParser.Event("delta", "a"), SseParser.Event("done", "b")),
            parseAll("event: delta\r\ndata: a\r\n\r\nevent: done\rdata: b\r\r"),
        )
    }

    @Test
    fun `an event split across chunks, including inside a CRLF`() {
        val events = parseAll("ev", "ent: del", "ta\r", "\ndata: {\"te", "xt\":\"hi\"}\r\n", "\r", "\n")
        assertEquals(listOf(SseParser.Event("delta", "{\"text\":\"hi\"}")), events)
    }

    @Test
    fun `only one leading space of the value is stripped and a field without colon has an empty value`() {
        val events = parseAll("data:  two spaces\ndata\n\n")
        assertEquals(listOf(SseParser.Event("message", " two spaces\n")), events)
    }

    @Test
    fun `an unterminated event at the end is not dispatched`() {
        val parser = SseParser()
        assertTrue(parser.feed("event: delta\ndata: partial\n").isEmpty())
    }

    @Test
    fun `event toString never contains the data`() {
        val text = SseParser.Event("delta", "my secret words").toString()
        assertFalse(text.contains("secret"), text)
    }
}
