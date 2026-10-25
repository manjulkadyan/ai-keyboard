/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.editing.testing

import com.manjul.aikeyboard.ime.editing.ComposingSpanController
import com.manjul.aikeyboard.ime.editing.TextTarget

/** What an editor reports to the IME after an edit (`onUpdateSelection`). */
data class SelectionUpdate(val selStart: Int, val selEnd: Int, val candStart: Int, val candEnd: Int)

/**
 * A host text field for tests: text, selection and one composing region, edited through `InputConnection`-like
 * calls (IME side) or the `host*` calls (the app or the user, not through the IME).
 *
 * Every character is a [Cell] that remembers which side wrote it, so [HostSafetyChecker] can tell a host character
 * from an equal one the IME wrote. The composing region behaves like Android's exclusive composing span: text
 * inserted at either end stays outside it, text inserted inside grows it, and it disappears once empty.
 *
 * Each outermost edit that changes anything queues one [SelectionUpdate]; nothing is delivered until the test calls
 * [deliverNext], [deliverDue] or [deliverAll], so updates can arrive as late as the test likes.
 *
 * The [TextTarget] methods follow `ImeTextTarget` in `:app` call for call, over this editor's own primitives.
 */
class FakeEditor(initial: String = "") : TextTarget {
    /** One character and the side that wrote it. Compared by identity. */
    class Cell(val char: Char, val host: Boolean)

    private val cells = ArrayList<Cell>()
    private val hostWritten = ArrayList<Cell>()
    private val hostRemoved = HashSet<Cell>()

    var selectionStart = 0
        private set
    var selectionEnd = 0
        private set
    var composingStart = -1
        private set
    var composingEnd = -1
        private set

    private var batchDepth = 0
    private var reported = state()
    private val updates = ArrayDeque<SelectionUpdate>()

    /** How many of the newest updates [deliverDue] holds back. */
    var selectionDelay = 0

    /** Whether the current region is ours (`ImeTextTarget.owned`). */
    private var owned = false

    /** Where our region and our last commit should be (`ImeTextTarget.region`, `ImeTextTarget.lastCommit`). */
    private var region: Anchor? = null
    private var lastCommit: Anchor? = null

    private class Anchor(val text: String, val after: String, val connection: Int)

    /** Bumped by [hostReplaceAll]: `setText` restarts input, so the IME talks to a new `InputConnection`. */
    private var connection = 0

    init {
        hostType(initial)
        updates.clear()
        reported = state()
    }

    val text: String get() = String(CharArray(cells.size) { cells[it].char })

    /** Host characters currently in the field. */
    val hostCells: List<Cell> get() = cells.filter { it.host }

    /** Every host character ever written, minus those the host itself removed: all must still be in the field. */
    val hostOwned: List<Cell> get() = hostWritten.filter { it !in hostRemoved }

    /** Whether [cell] is in the field right now (by identity). */
    fun contains(cell: Cell): Boolean = cells.any { it === cell }

    val pendingUpdates: List<SelectionUpdate> get() = updates.toList()

    // InputConnection-like primitives (IME side).

    fun beginBatchEdit() {
        batchDepth++
    }

    fun endBatchEdit() {
        check(batchDepth > 0) { "endBatchEdit without beginBatchEdit" }
        batchDepth--
        report()
    }

    fun setComposingText(text: String, newCursorPosition: Int): Boolean = edit {
        val (start, end) = replaceTarget()
        replace(start, end, imeCells(text), host = false)
        if (text.isEmpty()) {
            clearRegion()
        } else {
            composingStart = start
            composingEnd = start + text.length
        }
        placeCursor(start, start + text.length, newCursorPosition)
        true
    }

    fun commitText(text: String, newCursorPosition: Int): Boolean = edit {
        val (start, end) = replaceTarget()
        replace(start, end, imeCells(text), host = false)
        clearRegion()
        placeCursor(start, start + text.length, newCursorPosition)
        true
    }

    fun finishComposingText(): Boolean = edit {
        clearRegion()
        true
    }

    fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean = edit {
        require(beforeLength >= 0 && afterLength >= 0)
        val afterEnd = minOf(cells.size, selectionEnd + afterLength)
        replace(selectionEnd, afterEnd, emptyList(), host = false)
        replace(maxOf(0, selectionStart - beforeLength), selectionStart, emptyList(), host = false)
        true
    }

    fun getTextBeforeCursor(n: Int): String = text.substring(maxOf(0, selectionStart - n), selectionStart)

    fun getTextAfterCursor(n: Int): String = text.substring(selectionEnd, minOf(cells.size, selectionEnd + n))

    fun getSelectedText(): String? =
        if (selectionStart == selectionEnd) null else text.substring(selectionStart, selectionEnd)

    // TextTarget, as ImeTextTarget implements it.

    override fun setComposing(text: String): Boolean {
        beginBatchEdit()
        try {
            if (!ownsVerifiedRegion()) finishComposingText()
            owned = true
            val ok = setComposingText(text, 1)
            region = anchor(text)
            return ok
        } finally {
            endBatchEdit()
        }
    }

    override fun commit(text: String, removeSpaceBefore: Boolean): Boolean {
        beginBatchEdit()
        try {
            if (ownsVerifiedRegion()) setComposingText("", 1)
            finishComposingText()
            own(false)
            if (removeSpaceBefore && isAt(lastCommit) && getTextBeforeCursor(1) == " ") deleteSurroundingText(1, 0)
            val ok = commitText(text, 1)
            lastCommit = anchor(text)
            return ok
        } finally {
            endBatchEdit()
        }
    }

    override fun finishComposing(): Boolean {
        if (!owned) return true
        own(false)
        return finishComposingText()
    }

    override fun clearComposing(expected: String): Boolean {
        if (!owned) return false
        val verified = ownsVerifiedRegion()
        own(false)
        if (!verified || getTextBeforeCursor(expected.length) != expected) {
            finishComposingText()
            return false
        }
        beginBatchEdit()
        try {
            setComposingText("", 1)
            finishComposingText()
            return true
        } finally {
            endBatchEdit()
        }
    }

    override fun deleteBeforeCursorIfEquals(expected: String): Boolean {
        if (expected.isEmpty()) return false
        beginBatchEdit()
        try {
            if (!getSelectedText().isNullOrEmpty()) return false
            if (getTextBeforeCursor(expected.length) != expected) return false
            return deleteSurroundingText(expected.length, 0)
        } finally {
            endBatchEdit()
        }
    }

    /** The input connection changed (`ImeTextTarget.forgetOwnedRegion`). */
    fun forgetOwnedRegion() {
        own(false)
        lastCommit = null
    }

    private fun own(value: Boolean) {
        owned = value
        if (!value) region = null
    }

    private fun ownsVerifiedRegion(): Boolean = owned && isAt(region)

    private fun anchor(text: String) = Anchor(text, getTextAfterCursor(CONTEXT), connection)

    private fun isAt(anchor: Anchor?): Boolean = anchor != null && anchor.connection == connection &&
        getSelectedText().isNullOrEmpty() &&
        getTextBeforeCursor(anchor.text.length) == anchor.text && getTextAfterCursor(CONTEXT) == anchor.after

    // Host side: the app or the user editing the field without the IME.

    /** Types [s] at the cursor, replacing the selection. */
    fun hostType(s: String) = edit {
        val start = selectionStart
        replace(start, selectionEnd, s.map { Cell(it, host = true).also(hostWritten::add) }, host = true)
        selectionStart = start + s.length
        selectionEnd = selectionStart
    }

    /** Deletes the selection, or up to [n] characters before the cursor. */
    fun hostDelete(n: Int) = edit {
        val start = if (selectionStart != selectionEnd) selectionStart else maxOf(0, selectionStart - n)
        replace(start, selectionEnd, emptyList(), host = true)
    }

    /** Moves the cursor (or selects `start..end`). */
    fun hostMoveCursor(start: Int, end: Int = start) = edit {
        require(start in 0..end && end <= cells.size)
        selectionStart = start
        selectionEnd = end
    }

    /** Replaces the whole text (`setText`): the region goes, the cursor goes to the end, input restarts. */
    fun hostReplaceAll(s: String) = edit {
        connection++
        replace(0, cells.size, s.map { Cell(it, host = true).also(hostWritten::add) }, host = true)
        clearRegion()
        selectionStart = cells.size
        selectionEnd = cells.size
    }

    // Selection updates.

    /** Delivers the oldest queued update, if any. */
    fun deliverNext(span: ComposingSpanController) = deliverNext(span.sink())

    /** Delivers all but the newest [selectionDelay] updates. */
    fun deliverDue(span: ComposingSpanController) = deliverDue(span.sink())

    /** Delivers every queued update, including those queued while delivering. */
    fun deliverAll(span: ComposingSpanController) = deliverAll(span.sink())

    /** Like [deliverNext], to any receiver of `onUpdateSelection` (e.g. a controller that owns its span). */
    fun deliverNext(sink: (SelectionUpdate) -> Unit) {
        val u = updates.removeFirstOrNull() ?: return
        sink(u)
    }

    fun deliverDue(sink: (SelectionUpdate) -> Unit) {
        while (updates.size > selectionDelay) deliverNext(sink)
    }

    fun deliverAll(sink: (SelectionUpdate) -> Unit) {
        while (updates.isNotEmpty()) deliverNext(sink)
    }

    private fun ComposingSpanController.sink(): (SelectionUpdate) -> Unit =
        { u -> onSelectionUpdate(u.selStart, u.selEnd, u.candStart, u.candEnd) }

    // Internals.

    private fun <T> edit(block: () -> T): T {
        batchDepth++
        try {
            return block()
        } finally {
            batchDepth--
            report()
        }
    }

    private fun imeCells(text: String) = text.map { Cell(it, host = false) }

    /** What an IME write replaces: the composing region if there is one, otherwise the selection. */
    private fun replaceTarget(): Pair<Int, Int> =
        if (composingStart >= 0) composingStart to composingEnd else selectionStart to selectionEnd

    private fun placeCursor(start: Int, end: Int, newCursorPosition: Int) {
        val at = if (newCursorPosition > 0) end + newCursorPosition - 1 else start + newCursorPosition
        selectionStart = at.coerceIn(0, cells.size)
        selectionEnd = selectionStart
    }

    private fun clearRegion() {
        composingStart = -1
        composingEnd = -1
    }

    /** Replaces `start until end` with [inserted] and maps the selection and region like Android spans do. */
    private fun replace(start: Int, end: Int, inserted: List<Cell>, host: Boolean) {
        val removed = cells.subList(start, end)
        if (host) hostRemoved.addAll(removed)
        removed.clear()
        cells.addAll(start, inserted)
        val deleted = end - start
        val added = inserted.size
        fun afterDelete(p: Int) = if (p <= start) p else if (p >= end) p - deleted else start
        // Callers that insert at the cursor place it themselves; here only text after the edit shifts.
        selectionStart = afterDelete(selectionStart).let { if (it > start) it + added else it }
        selectionEnd = afterDelete(selectionEnd).let { if (it > start) it + added else it }
        if (composingStart >= 0) {
            val cs = afterDelete(composingStart)
            val ce = afterDelete(composingEnd)
            if (cs == ce) {
                clearRegion()
            } else {
                // Exclusive at both ends: an insertion at the start shifts it, at the end leaves it out.
                composingStart = if (cs >= start) cs + added else cs
                composingEnd = if (ce > start) ce + added else ce
            }
        }
    }

    private fun state() = SelectionUpdate(selectionStart, selectionEnd, composingStart, composingEnd) to text

    private fun report() {
        if (batchDepth > 0) return
        val now = state()
        if (now == reported) return
        reported = now
        updates.addLast(now.first)
    }

    private companion object {
        const val CONTEXT = 64
    }
}
