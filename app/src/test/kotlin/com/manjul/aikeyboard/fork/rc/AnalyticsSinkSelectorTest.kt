/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.rc

import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.AnalyticsSink
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals

class AnalyticsSinkSelectorTest {
    private class Recorder : AnalyticsSink {
        val events = mutableListOf<String>()
        val properties = mutableListOf<String>()
        override fun log(event: AnalyticsEvent) {
            events += event.name
        }

        override fun setUserProperty(name: String, value: String?) {
            properties += name
        }
    }

    private val event = AnalyticsEvent("ime_enabled", emptyMap())

    @Test
    fun `without Firebase every event goes to the fallback`() {
        val fallback = Recorder()
        val calls = mutableListOf<Boolean>()
        val selector = AnalyticsSinkSelector(null, fallback, { true }, { false }, { calls += it })
        selector.log(event)
        selector.onAllowedChanged(false)
        selector.syncWhenDisallowed()
        assertEquals(listOf("ime_enabled"), fallback.events)
        assertEquals(emptyList(), calls)
    }

    @Test
    fun `before consent Firebase gets no event, no user property and no collection call`() {
        val firebase = Recorder()
        val fallback = Recorder()
        val calls = mutableListOf<Boolean>()
        val selector = AnalyticsSinkSelector(firebase, fallback, { false }, { false }, { calls += it })
        selector.log(event)
        selector.log(event)
        selector.setUserProperty("is_pro", "false")
        selector.onAllowedChanged(false)
        assertEquals(emptyList(), firebase.events)
        assertEquals(emptyList(), firebase.properties)
        assertEquals(emptyList(), calls)
        assertEquals(2, fallback.events.size)
        assertEquals(listOf("is_pro"), fallback.properties)
    }

    @Test
    fun `Firebase gets events only while allowed and collection follows`() {
        val firebase = Recorder()
        val fallback = Recorder()
        var allowed = true
        val calls = mutableListOf<Boolean>()
        val selector = AnalyticsSinkSelector(firebase, fallback, { allowed }, { false }, { calls += it })
        selector.log(event)
        selector.log(event)
        assertEquals(2, firebase.events.size)
        assertEquals(listOf(true), calls)

        allowed = false
        selector.onAllowedChanged(false)
        selector.log(event)
        assertEquals(2, firebase.events.size)
        assertEquals(1, fallback.events.size)
        assertEquals(listOf(true, false), calls)

        allowed = true
        selector.onAllowedChanged(true)
        assertEquals(listOf(true, false, true), calls)
    }

    @Test
    fun `an allowed change before any event does not start Firebase`() {
        val calls = mutableListOf<Boolean>()
        val selector = AnalyticsSinkSelector(Recorder(), Recorder(), { true }, { false }, { calls += it })
        selector.onAllowedChanged(true)
        selector.onAllowedChanged(false)
        assertEquals(emptyList(), calls)
    }

    @Test
    fun `the app start sync turns a persisted collection flag off only while disallowed`() {
        val calls = mutableListOf<Boolean>()
        var allowed = false
        val selector = AnalyticsSinkSelector(Recorder(), Recorder(), { allowed }, { false }, { calls += it })
        selector.syncWhenDisallowed()
        selector.syncWhenDisallowed()
        assertEquals(listOf(false), calls)

        allowed = true
        selector.syncWhenDisallowed()
        selector.log(event)
        assertEquals(listOf(false, true), calls)
    }

    @Test
    fun `an opt-out turns collection off when Firebase runs although this process never turned it on`() {
        // Cold start of a consented user: the UMP result started Firebase Analytics (Consent Mode), the relay has
        // not flushed yet, so collecting is unknown, and the SDK still holds the persisted "on" flag.
        val calls = mutableListOf<Boolean>()
        var running = false
        val selector = AnalyticsSinkSelector(Recorder(), Recorder(), { false }, { running }, { calls += it })
        selector.onAllowedChanged(false)
        assertEquals(emptyList(), calls, "Firebase does not run: nothing to turn off, nothing started")

        running = true
        selector.onAllowedChanged(false)
        selector.onAllowedChanged(false)
        assertEquals(listOf(false), calls)
    }

    @Test
    fun `an opt-out during the allowed check of an event still leaves collection off`() {
        val calls = mutableListOf<Boolean>()
        val allowed = AtomicBoolean(true)
        val other = AtomicReference<Thread>()
        lateinit var selector: AnalyticsSinkSelector
        selector = AnalyticsSinkSelector(Recorder(), Recorder(), {
            if (other.get() == null) {
                // The opt-out arrives on another thread right after log() read "allowed".
                val t = Thread {
                    allowed.set(false)
                    selector.onAllowedChanged(false)
                }
                other.set(t)
                t.start()
                val until = System.currentTimeMillis() + 2_000
                while (t.state != Thread.State.BLOCKED && t.isAlive && System.currentTimeMillis() < until) {
                    Thread.yield()
                }
                true
            } else {
                allowed.get()
            }
        }, { false }, { synchronized(calls) { calls += it } })

        selector.log(event)
        other.get().join(5_000)

        assertEquals(listOf(true, false), synchronized(calls) { calls.toList() })
    }
}
