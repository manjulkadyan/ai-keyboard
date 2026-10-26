/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ai

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiPauseGateTest {
    private var now = 1_000_000L
    private val gate = AiPauseGate { now }

    @Test
    fun `not paused at first`() {
        assertFalse(gate.isPaused)
    }

    @Test
    fun `paused for the given time only`() {
        gate.pauseFor(5_000)
        assertTrue(gate.isPaused)
        now += 4_999
        assertTrue(gate.isPaused)
        now += 1
        assertFalse(gate.isPaused)
    }

    @Test
    fun `a shorter pause never shortens a longer one`() {
        gate.pauseFor(10_000)
        gate.pauseFor(1_000)
        now += 5_000
        assertTrue(gate.isPaused)
    }

    @Test
    fun `the Paused error pauses for one hour and other errors do not pause`() {
        gate.onError(AiError.Offline)
        gate.onError(AiError.Refused)
        assertFalse(gate.isPaused)
        gate.onError(AiError.Paused)
        assertTrue(gate.isPaused)
        now += AiPauseGate.PAUSED_MS - 1
        assertTrue(gate.isPaused)
        now += 1
        assertFalse(gate.isPaused)
    }

    @Test
    fun `clear lifts a pause`() {
        gate.pauseFor(10_000)
        gate.clear()
        assertFalse(gate.isPaused)
    }
}
