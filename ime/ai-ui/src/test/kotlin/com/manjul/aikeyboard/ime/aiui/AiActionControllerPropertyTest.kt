/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.aiui

import com.manjul.aikeyboard.core.ai.AiError
import com.manjul.aikeyboard.core.ai.AiEvent
import com.manjul.aikeyboard.core.ai.RewriteMode
import com.manjul.aikeyboard.ime.editing.testing.FakeEditor
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.fail
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Review Focus 1 and 2 over the whole controller: random interleavings of streamed deltas, host edits, cursor moves,
 * field switches, restriction flips and user taps. No host character ever goes except inside the captured range on a
 * REPLACED replace; the host text changes only through a replace, insert or undo the live decision and field
 * generation allow; after a restriction or field switch the sheet is hidden and a late delta changes nothing. A
 * failure names the seed, step and action log; messages hold no field text.
 */
class AiActionControllerPropertyTest {
    private val seen = HashMap<String, Int>()

    private fun saw(outcome: String) {
        seen[outcome] = (seen[outcome] ?: 0) + 1
    }

    @Test fun `no host text outside a replace or undo ever changes`() {
        for (seed in 0 until SEEDS) runTest { Run(this, seed).go() }
        println("AI property outcomes: $seen")
        for (outcome in listOf("replaced", "text changed", "undo", "insert", "late delta dropped", "restricted")) {
            if ((seen[outcome] ?: 0) < 20) fail("only ${seen[outcome] ?: 0} x $outcome over $SEEDS seeds")
        }
    }

    private inner class Run(private val scope: TestScope, private val seed: Int) {
        private val random = Random(seed)
        private val rig = AiRig(scope, host = words(random, random.nextInt(1, 8)))
        private val editor get() = rig.editor
        private val controller get() = rig.controller
        private val consented: MutableSet<FakeEditor.Cell> = Collections.newSetFromMap(IdentityHashMap())
        private val log = mutableListOf<String>()

        fun go() {
            for (step in 0 until STEPS) {
                step(step)
                for (cell in editor.hostOwned) {
                    if (cell !in consented && !editor.contains(cell)) fail(step, "a host char outside a replace went")
                }
            }
        }

        private fun allowed(): Boolean =
            rig.decision.allowAi && rig.decision.allowCloud && controller.captureGeneration == rig.generation

        /** Runs a user tap that may write: it may change the field only when the live decision and field allow it. */
        private fun write(step: Int, name: String, block: () -> Unit) {
            log += name
            val before = editor.snapshotCells()
            val ok = allowed()
            val range = controller.captured?.range
            val wasReplace = name == "replace"
            block()
            scope.runCurrent()
            val after = editor.snapshotCells()
            if (sameCells(before, after)) return
            if (!ok) fail(step, "$name wrote while the decision or field forbade it")
            val removed = before.filter { b -> after.none { it === b } }
            if (wasReplace) {
                if (controller.state.value !is AiSheetState.Replaced) fail(step, "replace changed the field without REPLACED")
                val r = range ?: fail(step, "replace without a captured range")
                val idx = removed.map { c -> before.indexOfFirst { it === c } }
                if (idx.any { it !in r }) fail(step, "replace removed text outside the captured range")
                saw("replaced")
                consented.addAll(removed)
            } else if (removed.any { it.host }) {
                fail(step, "$name removed a host char")
            }
        }

        private fun step(step: Int) {
            val size = editor.text.length
            when (random.nextInt(23)) {
                0, 1 -> {
                    log += "open+tone"
                    controller.open()
                    val modes = RewriteMode.entries
                    controller.chooseTone(modes[random.nextInt(modes.size)])
                    scope.runCurrent()
                }
                2, 3, 4 -> if (rig.replies.isNotEmpty()) {
                    log += "delta"
                    val visible = controller.state.value
                    val stale = !allowed()
                    rig.delta(words(random, 1) + " ")
                    if (stale && visible is AiSheetState.Streaming) {
                        if (controller.state.value != AiSheetState.Hidden) fail(step, "a stale delta was shown")
                        saw("late delta dropped")
                    }
                }
                5, 20 -> if (rig.replies.isNotEmpty()) {
                    log += "done"
                    rig.done(trimmed = random.nextInt(5) == 0)
                }
                6 -> if (rig.replies.isNotEmpty()) {
                    log += "fail"
                    val errors = listOf(AiError.Offline, AiError.Timeout, AiError.Refused, AiError.Unavailable)
                    rig.fail(errors[random.nextInt(errors.size)])
                }
                7, 8 -> {
                    log += "hostReplace"
                    val a = random.nextInt(size + 1)
                    val b = random.nextInt(a, minOf(size, a + 4) + 1)
                    editor.hostReplace(a, b, if (random.nextBoolean()) "" else words(random, 1))
                }
                9 -> {
                    log += "move"
                    val a = random.nextInt(size + 1)
                    editor.hostMoveCursor(a, if (random.nextBoolean()) a else random.nextInt(a, size + 1))
                }
                10 -> {
                    log += "fieldSwitch"
                    rig.generation++
                    if (random.nextBoolean()) {
                        controller.onFieldChanged()
                        if (controller.state.value != AiSheetState.Hidden) fail(step, "field change left the sheet")
                    }
                }
                11 -> {
                    if (rig.decision.allowAi && random.nextBoolean()) {
                        log += "restrict"
                        rig.decision = AiRig.PASSWORD
                        if (controller.state.value is AiSheetState.Streaming) saw("restricted")
                        if (random.nextBoolean()) {
                            controller.onRestricted()
                            if (controller.state.value != AiSheetState.Hidden) fail(step, "restriction left the sheet")
                        }
                    } else {
                        log += "unrestrict"
                        rig.decision = PolicyDecision.Unrestricted
                        controller.refreshAvailability()
                    }
                }
                12, 13 -> {
                    write(step, "replace") {
                        controller.replace()
                        if (controller.state.value is AiSheetState.TextChanged) saw("text changed")
                    }
                    // Often follow a replace with an undo, sometimes after a host edit or some time.
                    if (controller.state.value is AiSheetState.Replaced && random.nextInt(4) != 0) {
                        when (random.nextInt(8)) {
                            0 -> {
                                log += "hostReplace"
                                val n = editor.text.length
                                val a = random.nextInt(n + 1)
                                editor.hostReplace(a, random.nextInt(a, minOf(n, a + 3) + 1), words(random, 1))
                            }
                            1 -> {
                                log += "time"
                                scope.advanceTimeBy(random.nextLong(0, 7_000))
                                scope.runCurrent()
                            }
                        }
                        write(step, "undo") { if (controller.undo()) saw("undo") }
                    }
                }
                14 -> write(step, "insert") {
                    val before = controller.state.value
                    val ok = allowed()
                    controller.insert()
                    if ((before is AiSheetState.Ready || before is AiSheetState.TextChanged) &&
                        controller.state.value == AiSheetState.Hidden && ok
                    ) {
                        saw("insert")
                    }
                }
                15 -> write(step, "undo") { if (controller.undo()) saw("undo") }
                16 -> write(step, "stop") { controller.stop() }
                17 -> write(step, "retry") { controller.retry() }
                18 -> write(step, "dismiss") { controller.dismiss() }
                19 -> write(step, "stillWorking") {
                    if (rig.replies.isNotEmpty()) rig.reply(Step.Event(AiEvent.StillWorking))
                }
                else -> {
                    log += "time"
                    scope.advanceTimeBy(random.nextLong(0, 3_000))
                    scope.runCurrent()
                }
            }
        }

        private fun fail(step: Int, what: String): Nothing =
            fail("seed $seed step $step: $what\n${log.takeLast(15).joinToString(" > ")}")
    }

    private fun sameCells(a: List<FakeEditor.Cell>, b: List<FakeEditor.Cell>) =
        a.size == b.size && a.indices.all { a[it] === b[it] }

    private companion object {
        const val SEEDS = 500
        const val STEPS = 150
        val WORDS = listOf("helo", "wrld", "the", "cat", "شكرا", "नमस्ते", "a")

        fun words(random: Random, n: Int): String = List(n) { WORDS[random.nextInt(WORDS.size)] }.joinToString(" ")
    }
}
