/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ai

/**
 * An incremental `text/event-stream` parser (WHATWG HTML "Server-sent events"): feed it text in any chunks
 * and it returns each complete event. Lines end in LF, CRLF or CR (a CRLF split across two chunks counts once);
 * `:` lines are comments; `data` lines are joined with LF; `id` and `retry` are ignored; an event with no data
 * is dropped. Not thread-safe: one parser per stream.
 */
class SseParser {
    /** One dispatched event. [data] is user content, so [toString] prints only its length. */
    data class Event(val event: String, val data: String) {
        override fun toString(): String = "Event($event, chars=${data.length})"
    }

    private val line = StringBuilder()
    private val data = StringBuilder()
    private var eventType: String? = null
    private var hasData = false
    private var skipLf = false

    fun feed(chunk: String): List<Event> {
        var out: MutableList<Event>? = null
        for (c in chunk) {
            if (skipLf) {
                skipLf = false
                if (c == '\n') continue
            }
            when (c) {
                '\r', '\n' -> {
                    skipLf = c == '\r'
                    endLine()?.let { (out ?: mutableListOf<Event>().also { out = it }).add(it) }
                }
                else -> line.append(c)
            }
        }
        return out ?: emptyList()
    }

    private fun endLine(): Event? {
        if (line.isEmpty()) return dispatch()
        val text = line.toString()
        line.setLength(0)
        if (text.startsWith(':')) return null
        val colon = text.indexOf(':')
        val field = if (colon < 0) text else text.substring(0, colon)
        var value = if (colon < 0) "" else text.substring(colon + 1)
        if (value.startsWith(' ')) value = value.substring(1)
        when (field) {
            "event" -> eventType = value
            "data" -> {
                if (hasData) data.append('\n')
                data.append(value)
                hasData = true
            }
        }
        return null
    }

    private fun dispatch(): Event? {
        val event = if (hasData) Event(eventType?.takeIf { it.isNotEmpty() } ?: "message", data.toString()) else null
        data.setLength(0)
        hasData = false
        eventType = null
        return event
    }
}
