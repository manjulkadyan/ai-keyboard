/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate

/**
 * A piece of text to translate on its own. [body] goes to the engine; [separatorAfter] (whitespace and
 * newlines) is put back verbatim after the translated body. Both are user content; [toString] omits them.
 */
data class Chunk(val body: String, val separatorAfter: String) {
    override fun toString(): String = "Chunk(chars=${body.length}, separatorChars=${separatorAfter.length})"
}

/**
 * Splits long text into sentence chunks for the engine and joins the translations back (TRD FR-06.6).
 *
 * Every character of the input lands in exactly one [Chunk.body] or [Chunk.separatorAfter], so
 * `join(split(x), split(x).map { it.body }) == x`: no delimiter is substituted or introduced. Sentences end at
 * `. ? ! ۔ ؟ । ؛ …` (plus closing quotes/brackets) followed by whitespace, or at a newline. One chunk per
 * sentence, so whitespace and paragraph breaks between sentences are never sent to the engine (which may drop
 * them). A sentence longer than `maxChars` is split on whitespace; a single word longer than that is left
 * whole. Text is user content, never logged.
 */
object SentenceChunker {
    const val DEFAULT_MAX_CHARS = 500

    private val TERMINATORS = setOf('.', '?', '!', '۔', '؟', '।', '؛', '…')
    private val CLOSERS = setOf('"', '\'', '”', '’', ')', ']', '»', '」', '』')

    fun split(text: String, maxChars: Int = DEFAULT_MAX_CHARS): List<Chunk> {
        require(maxChars > 0) { "maxChars must be positive" }
        if (text.isEmpty()) return emptyList()
        return sentences(text).flatMap { splitLong(it, maxChars) }
    }

    fun join(chunks: List<Chunk>, translated: List<String>): String {
        require(chunks.size == translated.size) { "chunks and translations differ in size" }
        return buildString {
            chunks.forEachIndexed { i, chunk ->
                append(translated[i])
                append(chunk.separatorAfter)
            }
        }
    }

    /** Sentence units; leading whitespace becomes a unit with an empty body. */
    private fun sentences(text: String): List<Chunk> {
        val out = mutableListOf<Chunk>()
        var i = 0
        val n = text.length
        val lead = whitespaceEnd(text, 0)
        if (lead > 0) {
            out += Chunk("", text.substring(0, lead))
            i = lead
        }
        var start = i
        while (i < n) {
            val c = text[i]
            when {
                c in TERMINATORS -> {
                    var end = i + 1
                    while (end < n && (text[end] in TERMINATORS || text[end] in CLOSERS)) end++
                    val wsEnd = whitespaceEnd(text, end)
                    if (wsEnd > end || end == n) {
                        out += Chunk(text.substring(start, end), text.substring(end, wsEnd))
                        start = wsEnd
                    }
                    i = if (wsEnd > end) wsEnd else end
                }
                c == '\n' || c == '\r' -> {
                    // Trailing spaces before the newline belong to the separator, not the body.
                    var bodyEnd = i
                    while (bodyEnd > start && text[bodyEnd - 1].isWhitespace()) bodyEnd--
                    val wsEnd = whitespaceEnd(text, i)
                    out += Chunk(text.substring(start, bodyEnd), text.substring(bodyEnd, wsEnd))
                    start = wsEnd
                    i = wsEnd
                }
                else -> i++
            }
        }
        if (start < n) {
            var bodyEnd = n
            while (bodyEnd > start && text[bodyEnd - 1].isWhitespace()) bodyEnd--
            out += Chunk(text.substring(start, bodyEnd), text.substring(bodyEnd, n))
        }
        return out
    }

    /** Splits a body longer than [maxChars] at whitespace runs; the last piece keeps the unit's separator. */
    private fun splitLong(unit: Chunk, maxChars: Int): List<Chunk> {
        if (unit.body.length <= maxChars) return listOf(unit)
        val body = unit.body
        val out = mutableListOf<Chunk>()
        var pieceStart = 0
        var lastBreak = -1 // index of the first whitespace char of the last run inside the current piece
        var i = 0
        while (i < body.length) {
            if (body[i].isWhitespace()) {
                val runEnd = whitespaceEnd(body, i)
                if (i - pieceStart > maxChars && lastBreak > pieceStart) {
                    val breakEnd = whitespaceEnd(body, lastBreak)
                    out += Chunk(body.substring(pieceStart, lastBreak), body.substring(lastBreak, breakEnd))
                    pieceStart = breakEnd
                }
                lastBreak = i
                i = runEnd
            } else {
                i++
            }
        }
        if (body.length - pieceStart > maxChars && lastBreak > pieceStart) {
            val breakEnd = whitespaceEnd(body, lastBreak)
            out += Chunk(body.substring(pieceStart, lastBreak), body.substring(lastBreak, breakEnd))
            pieceStart = breakEnd
        }
        out += Chunk(body.substring(pieceStart), unit.separatorAfter)
        return out
    }

    private fun whitespaceEnd(text: String, from: Int): Int {
        var i = from
        while (i < text.length && text[i].isWhitespace()) i++
        return i
    }
}
