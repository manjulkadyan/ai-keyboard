/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.editing

import com.manjul.aikeyboard.ime.editing.testing.FakeEditor
import com.manjul.aikeyboard.ime.editing.testing.HostSafetyChecker
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.fail

/**
 * Review Focus 1 harness: whatever the host does and however late its selection updates arrive, the IME side never
 * deletes or changes a character the host wrote. Random sequences over the composing-span operations, host edits,
 * cursor moves and late or batched selection updates; a failure names the seed and step to replay.
 */
class ComposingSpanPropertyTest {
    @Test fun imeSideNeverDeletesOrChangesHostText() {
        for (seed in 0 until SEEDS) {
            val random = Random(seed)
            val editor = FakeEditor(word(random, maxLength = 12))
            editor.selectionDelay = random.nextInt(4)
            val span = ComposingSpanController(editor)
            val checker = HostSafetyChecker(editor)
            val log = mutableListOf<String>()
            for (step in 0 until STEPS) {
                log += step(random, editor, span)
                check(seed, step, log, checker)
            }
            editor.deliverAll(span)
            span.discard()
            check(seed, STEPS, log, checker)
        }
    }

    /** One random step; returns its name (no text) for the failure message. */
    private fun step(random: Random, editor: FakeEditor, span: ComposingSpanController): String =
        when (random.nextInt(14)) {
            0, 1, 2 -> "update".also { span.update(word(random, maxLength = 6)) }
            3 -> "commit".also { span.commit(phrase(random)) }
            4 -> "drop".also { span.drop() }
            5 -> "discard".also { span.discard() }
            6 -> "startSession".also { span.startSession() }
            7 -> "hostType".also { editor.hostType(word(random, maxLength = 3)) }
            8 -> "hostDelete".also { editor.hostDelete(1 + random.nextInt(3)) }
            9 -> {
                val at = random.nextInt(editor.text.length + 1)
                "hostMoveCursor($at)".also { editor.hostMoveCursor(at) }
            }
            10 -> "hostReplaceAll".also { editor.hostReplaceAll(word(random, maxLength = 8)) }
            11 -> "deliverOne".also { editor.deliverNext(span) }
            12 -> "deliverDue".also { editor.deliverDue(span) }
            else -> "deliverAll".also { editor.deliverAll(span) }
        }

    private fun check(seed: Int, step: Int, log: List<String>, checker: HostSafetyChecker) {
        val violations = checker.violations()
        if (violations.isNotEmpty()) {
            fail("seed $seed, after step $step: $violations\nsteps: ${log.joinToString()}")
        }
    }

    /** A small alphabet, so the IME's own text often looks exactly like the host's. */
    private fun word(random: Random, maxLength: Int): String =
        String(CharArray(1 + random.nextInt(maxLength)) { ALPHABET[random.nextInt(ALPHABET.length)] })

    private fun phrase(random: Random): String = when (random.nextInt(4)) {
        0 -> ", "
        1 -> ""
        else -> word(random, maxLength = 5) + " "
    }

    private companion object {
        const val SEEDS = 1_000
        const val STEPS = 60
        const val ALPHABET = "ab "
    }
}
