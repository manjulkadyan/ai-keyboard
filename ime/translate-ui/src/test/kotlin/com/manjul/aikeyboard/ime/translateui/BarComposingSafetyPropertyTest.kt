/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.translateui

import com.manjul.aikeyboard.core.datastore.BarMode
import com.manjul.aikeyboard.core.translate.EngineId
import com.manjul.aikeyboard.core.translate.FailKind
import com.manjul.aikeyboard.core.translate.Support
import com.manjul.aikeyboard.core.translate.TranslateResult
import com.manjul.aikeyboard.core.translate.testing.FakeTranslationEngine
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random
import kotlin.test.fail

/**
 * Review Focus 1 and PRD FR-13 AC1/AC2 for the whole bar: random sequences of bar keys, swaps, inserts (also while a
 * commit waits for its translation), undos, closes, mode switches, host edits and cursor moves, policy restrictions
 * and field changes, with engine responses delayed 0–2,000 ms (outliving cancellation for half the seeds) and random
 * engine failures, in preview and live mode. No host character may ever be deleted or changed by the bar, and the engine never has more than one request
 * in flight. A failure names the seed and the steps to replay.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BarComposingSafetyPropertyTest {
    @Test fun barNeverDeletesOrChangesHostText() {
        for (seed in 0 until SEEDS) runSeed(seed)
    }

    private fun runSeed(seed: Int) = runTest {
        val random = Random(seed)
        val engineRandom = Random(seed.toLong() shl 20)
        val engine = FakeTranslationEngine(
            delays = { engineRandom.nextLong(MAX_ENGINE_DELAY_MS + 1) },
            results = { req ->
                val failure = when (engineRandom.nextInt(10)) {
                    0 -> FailKind.OFFLINE
                    1 -> FailKind.ENGINE
                    else -> null
                }
                failure?.let { TranslateResult.Failed(it) }
                    ?: TranslateResult.Ok("<${req.target}>${req.text}", req.source!!, EngineId.MLKIT)
            },
            supports = { if (engineRandom.nextInt(12) == 0) Support.NEEDS_DOWNLOAD else Support.ON_DEVICE },
            ignoreCancellation = random.nextBoolean(),
        )
        val rig = BarRig(this, engine, host = word(random, maxLength = 12))
        rig.editor.selectionDelay = random.nextInt(4)
        rig.mode = if (random.nextBoolean()) BarMode.LIVE else BarMode.PREVIEW
        val log = mutableListOf<String>()
        rig.controller.open()
        runCurrent()
        for (step in 0 until STEPS) {
            log += step(random, rig)
            runCurrent()
            check(seed, step, log, rig)
            val wait = random.nextLong(MAX_STEP_WAIT_MS + 1)
            advanceTimeBy(wait)
            runCurrent()
            check(seed, step, log, rig)
        }
        advanceTimeBy(MAX_ENGINE_DELAY_MS * 2)
        runCurrent()
        rig.deliver()
        rig.controller.close(CloseReason.USER)
        rig.deliver()
        check(seed, STEPS, log, rig)
    }

    /** One random step; returns its name (no text) for the failure message. */
    private fun TestScope.step(random: Random, rig: BarRig): String {
        val c = rig.controller
        return when (random.nextInt(28)) {
            0, 1, 2 -> "type".also { c.buffer.type(word(random, maxLength = 4)) }
            3 -> "backspace".also { c.buffer.backspace() }
            4 -> "paste".also { c.buffer.paste(word(random, maxLength = 8)) }
            5 -> "swap".also { c.swapPair() }
            6, 7 -> "insert".also { c.insert() }
            8 -> "undo".also { c.undo() }
            9 -> "closeUser".also { c.close(CloseReason.USER) }
            10 -> "closeHidden".also { c.close(CloseReason.HIDDEN) }
            // Weighted like the closing steps (9, 10, 16), so the bar is open about half the time.
            11, 22, 23 -> "reopen".also {
                rig.decision = PolicyDecision.Unrestricted
                c.open()
            }
            12 -> {
                val m = if (random.nextBoolean()) BarMode.LIVE else BarMode.PREVIEW
                "setMode($m)".also { c.setMode(m) }
            }
            13 -> "hostType".also { rig.editor.hostType(word(random, maxLength = 3)) }
            14 -> "hostDelete".also { rig.editor.hostDelete(1 + random.nextInt(3)) }
            15 -> {
                val at = random.nextInt(rig.editor.text.length + 1)
                "hostMoveCursor($at)".also { rig.editor.hostMoveCursor(at) }
            }
            16 -> "policyRestricted".also {
                rig.decision = BarRig.PASSWORD
                c.onPolicyRestricted(BarRig.PASSWORD)
            }
            17 -> "fieldChanged".also {
                // A new field with the same connection (setText restarts input).
                rig.editor.hostReplaceAll(word(random, maxLength = 8))
                rig.editor.forgetOwnedRegion()
                rig.generation++
                c.onFieldChanged()
            }
            18 -> "generationBump".also { rig.generation++ }
            19 -> "deliverOne".also { rig.deliverOne() }
            20 -> "deliverDue".also { rig.editor.deliverDue(rig::forward) }
            21 -> "retry".also { c.retry() }
            // Enter again while a commit waits for its translation; without one, a key and Enter make one first.
            24 -> "insertWhilePending".also {
                val pending = (c.ui.value as? BarUiState.Open)?.commitPending == true
                if (!pending) {
                    c.buffer.type(word(random, maxLength = 4))
                    runCurrent()
                    c.insert()
                }
                c.insert()
            }
            // Long enough for the debounce and the slowest response, so inserts and undos find a preview.
            else -> "settle".also { advanceTimeBy(DEBOUNCE_MS + MAX_ENGINE_DELAY_MS + 1) }
        }.also { runCurrent() }
    }

    private fun check(seed: Int, step: Int, log: List<String>, rig: BarRig) {
        val violations = rig.checker.violations()
        if (violations.isNotEmpty()) {
            fail("seed $seed, after step $step: $violations\nsteps: ${log.joinToString()}")
        }
        if (rig.engine.maxInFlight > 1) {
            val inFlight = rig.engine.maxInFlight
            fail("seed $seed, after step $step: $inFlight engine calls in flight\nsteps: ${log.joinToString()}")
        }
    }

    /** A small alphabet, so the bar's text often looks like the host's. */
    private fun word(random: Random, maxLength: Int): String =
        String(CharArray(1 + random.nextInt(maxLength)) { ALPHABET[random.nextInt(ALPHABET.length)] })

    private companion object {
        const val SEEDS = 1_000
        const val STEPS = 80
        const val MAX_ENGINE_DELAY_MS = 2_000L
        const val MAX_STEP_WAIT_MS = 700L
        const val DEBOUNCE_MS = 450L
        const val ALPHABET = "ab "
    }
}
