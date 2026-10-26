/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.editing

import com.manjul.aikeyboard.ime.editing.testing.FakeEditor
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.fail

/**
 * Review Focus 1: whatever the host does between capture, Replace and Undo, every character outside the captured
 * range is preserved, and the IME side changes the text only on REPLACED (the captured range, and only while it still
 * holds the captured text) or on a successful undo (only our own output, with the field exactly as our replace left
 * it). Host characters the IME removes outside a successful Replace or undo fail the run. A host edit that leaves the
 * whole field equal to what our replace left cannot be told from no edit; undo then restores the original, which is
 * what the user sees as right. A failure names the seed and step; messages hold no field text.
 */
class SafeReplacerPropertyTest {
    @Test fun replaceAndUndoNeverTouchTextOutsideTheirRange() {
        for (seed in 0 until SEEDS) Run(seed).go()
        // The run must reach every outcome, or it proves nothing.
        for (outcome in listOf("REPLACED", "TEXT_CHANGED", "undo", "undo blocked", "insert")) {
            if ((seen[outcome] ?: 0) < 50) fail("only ${seen[outcome] ?: 0} x $outcome over $SEEDS seeds")
        }
    }

    private val seen = HashMap<String, Int>()

    private fun saw(outcome: String) {
        seen[outcome] = (seen[outcome] ?: 0) + 1
    }

    private class Done(val start: Int, val output: String, val original: String, val field: String)

    private inner class Run(private val seed: Int) {
        private val random = Random(seed)
        private var now = 0L
        private val editor = FakeEditor(words(random.nextInt(0, 12)))
        private val replacer = SafeReplacer(editor, clock = { now })
        private val consented: MutableSet<FakeEditor.Cell> = Collections.newSetFromMap(IdentityHashMap())
        private var captured: CapturedText? = null
        private var done: Done? = null
        private val log = mutableListOf<String>()

        fun go() {
            for (step in 0 until STEPS) {
                step(step)
                for (cell in editor.hostOwned) {
                    if (cell !in consented && !editor.contains(cell)) fail(step, "a host char outside a replace went")
                }
            }
        }

        private fun step(step: Int) {
            val size = editor.text.length
            when (random.nextInt(10)) {
                0 -> {
                    log += "hostType"
                    editor.hostMoveCursor(random.nextInt(size + 1))
                    editor.hostType(words(random.nextInt(1, 3)))
                }
                1 -> {
                    log += "hostReplace"
                    val a = random.nextInt(size + 1)
                    val b = random.nextInt(a, size + 1)
                    editor.hostReplace(a, b, if (random.nextBoolean()) "" else words(1))
                }
                2 -> {
                    log += "move"
                    val a = random.nextInt(size + 1)
                    editor.hostMoveCursor(a, if (random.nextBoolean()) a else random.nextInt(a, size + 1))
                }
                3, 4 -> {
                    log += "capture"
                    captured = TextCapture.capture(editor, maxChars = random.nextInt(4, 40))
                }
                5, 6 -> replace(step)
                7 -> undo(step)
                8 -> insert(step)
                else -> {
                    log += "time"
                    now += random.nextLong(0, 3_000)
                    if (random.nextInt(4) == 0) replacer.invalidate()
                }
            }
        }

        private fun replace(step: Int) {
            val target = captured ?: return
            log += "replace"
            val before = editor.snapshotCells()
            val output = words(random.nextInt(0, 3))
            val result = replacer.replace(target, output)
            val after = editor.snapshotCells()
            saw(result.name)
            if (result != ReplaceResult.REPLACED) {
                if (!same(before, after)) fail(step, "a $result replace changed the field")
                return
            }
            val start = target.range.first
            val end = target.range.last + 1
            if (end > before.size || text(before.subList(start, end)) != target.text) {
                fail(step, "replaced a range that no longer held the captured text")
            }
            keptAround(step, before, after, start, end, output.length)
            if (text(after.subList(start, start + output.length)) != output) fail(step, "output not in place")
            consented += before.subList(start, end).filter { it.host }
            done = Done(start, output, target.text, editor.text)
        }

        private fun undo(step: Int) {
            log += "undo"
            val before = editor.snapshotCells()
            val ok = replacer.undo()
            val after = editor.snapshotCells()
            val last = done
            done = null
            if (last != null) saw(if (ok) "undo" else "undo blocked")
            if (!ok) {
                if (!same(before, after)) fail(step, "a failed undo changed the field")
                return
            }
            if (last == null) fail(step, "undo with nothing replaced")
            val end = last.start + last.output.length
            keptAround(step, before, after, last.start, end, last.original.length)
            if (text(before) != last.field) fail(step, "undo after the host changed the field")
            consented += before.subList(last.start, end).filter { it.host }
            if (text(after.subList(last.start, last.start + last.original.length)) != last.original) {
                fail(step, "undo did not restore the original")
            }
        }

        private fun insert(step: Int) {
            log += "insert"
            val before = editor.snapshotCells()
            val ok = replacer.insert(words(1))
            val after = editor.snapshotCells()
            done = null
            if (ok) saw("insert")
            if (!ok) {
                if (!same(before, after)) fail(step, "a failed insert changed the field")
                return
            }
            val remaining = Collections.newSetFromMap(IdentityHashMap<FakeEditor.Cell, Boolean>()).apply { addAll(after) }
            if (before.any { it !in remaining }) fail(step, "insert removed text")
        }

        /** `before[0, start)` and `before[end, size)` are the same cells at the start and end of [after]. */
        private fun keptAround(
            step: Int,
            before: List<FakeEditor.Cell>,
            after: List<FakeEditor.Cell>,
            start: Int,
            end: Int,
            written: Int,
        ) {
            if (after.size != before.size - (end - start) + written) fail(step, "length off")
            for (i in 0 until start) if (before[i] !== after[i]) fail(step, "text before the range changed")
            val tail = before.size - end
            for (i in 0 until tail) {
                if (before[end + i] !== after[start + written + i]) fail(step, "text after the range changed")
            }
        }

        private fun same(a: List<FakeEditor.Cell>, b: List<FakeEditor.Cell>) =
            a.size == b.size && a.indices.all { a[it] === b[it] }

        private fun text(cells: List<FakeEditor.Cell>) = String(CharArray(cells.size) { cells[it].char })

        private fun words(n: Int): String = (0 until n).joinToString("") {
            String(CharArray(random.nextInt(1, 6)) { "abcde"[random.nextInt(5)] }) + if (random.nextInt(6) == 0) "\n" else " "
        }

        private fun fail(step: Int, what: String): Nothing =
            fail("seed $seed step $step: $what after ${log.takeLast(8)}")
    }

    private companion object {
        const val SEEDS = 1_000
        const val STEPS = 60
    }
}
