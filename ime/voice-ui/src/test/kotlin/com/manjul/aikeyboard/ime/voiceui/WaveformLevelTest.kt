/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.voiceui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformLevelTest {
    @Test fun riseIsGradualButALoudWordReaches80PercentIn150Ms() {
        val s = WaveSmoother()
        val first = s.step(1f)
        assertTrue("first tick is not a jump: $first", first < 0.5f)
        s.step(1f)
        val third = s.step(1f)
        assertTrue("about 80% after 3 ticks: $third", third >= 0.8f)
    }

    @Test fun singleSpikeIsDamped() {
        val s = WaveSmoother()
        val peak = maxOf(s.step(1f), s.step(0f), s.step(0f))
        assertTrue("one spiky callback stays low: $peak", peak < 0.4f)
    }

    @Test fun silenceDecaysToTheFloor() {
        val s = WaveSmoother()
        repeat(10) { s.step(1f) }
        var shown = 1f
        repeat(40) { shown = s.step(0f) }
        assertTrue("silence fades out: $shown", shown < 0.01f)
    }

    @Test fun repeatedReadingKeepsTheBarsMoving() {
        val s = WaveSmoother()
        val steps = List(5) { s.step(0.5f) }
        assertTrue("each tick still updates toward the level", steps.zipWithNext().all { (a, b) -> b > a })
        repeat(30) { s.step(0.5f) }
        assertEquals(0.5f, s.step(0.5f), 1e-3f)
    }
}
