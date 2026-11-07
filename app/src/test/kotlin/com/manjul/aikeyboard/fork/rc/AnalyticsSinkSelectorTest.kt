/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.rc

import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.AnalyticsSink
import kotlin.test.Test
import kotlin.test.assertEquals

class AnalyticsSinkSelectorTest {
    private class Recorder : AnalyticsSink {
        val events = mutableListOf<String>()
        override fun log(event: AnalyticsEvent) {
            events += event.name
        }
    }

    private val event = AnalyticsEvent("ime_enabled", emptyMap())

    @Test
    fun `without Firebase every event goes to the fallback`() {
        val fallback = Recorder()
        val calls = mutableListOf<Boolean>()
        val selector = AnalyticsSinkSelector(null, fallback, { false }, { calls += it })
        selector.log(event)
        selector.onOptOutChanged(true)
        assertEquals(listOf("ime_enabled"), fallback.events)
        assertEquals(emptyList(), calls)
    }

    @Test
    fun `Firebase gets events only without opt-out and collection follows the opt-out`() {
        val firebase = Recorder()
        val fallback = Recorder()
        var optOut = false
        val calls = mutableListOf<Boolean>()
        val selector = AnalyticsSinkSelector(firebase, fallback, { optOut }, { calls += it })
        selector.log(event)
        selector.log(event)
        assertEquals(2, firebase.events.size)
        assertEquals(listOf(true), calls)

        optOut = true
        selector.onOptOutChanged(true)
        selector.log(event)
        assertEquals(2, firebase.events.size)
        assertEquals(1, fallback.events.size)
        assertEquals(listOf(true, false), calls)

        optOut = false
        selector.onOptOutChanged(false)
        assertEquals(listOf(true, false, true), calls)
    }

    @Test
    fun `an opt-in before any event does not start Firebase`() {
        val calls = mutableListOf<Boolean>()
        val selector = AnalyticsSinkSelector(Recorder(), Recorder(), { false }, { calls += it })
        selector.onOptOutChanged(false)
        assertEquals(emptyList(), calls)
    }
}
