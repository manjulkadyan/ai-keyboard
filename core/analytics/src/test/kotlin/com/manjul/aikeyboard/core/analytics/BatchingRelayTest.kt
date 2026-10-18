/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.analytics

import com.manjul.aikeyboard.ime.policy.PolicyDecision
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class BatchingRelayTest {
    private class Recorder : AnalyticsSink {
        val flushed = mutableListOf<AnalyticsEvent>()
        override fun log(event: AnalyticsEvent) { flushed += event }
    }

    private fun TestScope.relay(
        sink: AnalyticsSink,
        optOut: () -> Boolean = { false },
        policy: () -> PolicyDecision? = { null },
    ) = BatchingRelay(sink, { currentTime }, this, optOut, policy)

    @Test fun batchesIntoOneFlushAtSixtySeconds() = runTest {
        val sink = Recorder()
        val relay = relay(sink)
        relay.log(EventCatalog.onboardingStart("launcher"))
        advanceTimeBy(5_000)
        relay.log(EventCatalog.onboardingStepView("1"))
        advanceTimeBy(5_000)
        relay.log(EventCatalog.onboardingStepView("2"))
        advanceTimeBy(49_000) // t = 59 s
        assertEquals(0, sink.flushed.size)
        advanceTimeBy(1_001)
        assertEquals(3, sink.flushed.size)
        advanceTimeBy(120_000)
        assertEquals(3, sink.flushed.size)
    }

    @Test fun nextEventAfterFlushStartsNewBatch() = runTest {
        val sink = Recorder()
        val relay = relay(sink)
        relay.log(EventCatalog.privacyPrivateAppAdd())
        advanceTimeBy(60_001)
        relay.log(EventCatalog.privacyPrivateAppAdd())
        advanceTimeBy(59_000)
        assertEquals(1, sink.flushed.size)
        advanceTimeBy(1_100)
        assertEquals(2, sink.flushed.size)
    }

    @Test fun optOutDropsEverything() = runTest {
        val sink = Recorder()
        var out = true
        val relay = relay(sink, optOut = { out })
        relay.log(EventCatalog.privacyPrivateAppAdd())
        advanceTimeBy(61_000)
        assertEquals(0, sink.flushed.size)
        out = false
        relay.log(EventCatalog.privacyPrivateAppAdd())
        out = true // opts out before the flush
        advanceTimeBy(61_000)
        assertEquals(0, sink.flushed.size)
    }

    @Test fun minimalPolicyOnlyPassesSensitiveSessionStart() = runTest {
        val sink = Recorder()
        val relay = relay(sink, policy = { PolicyDecision.Locked })
        relay.log(EventCatalog.imeSessionStart("en", false))
        relay.log(EventCatalog.voiceSessionStart("en", "whisper"))
        relay.log(EventCatalog.imeSessionStart("en", true))
        advanceTimeBy(61_000)
        assertEquals(listOf(EventCatalog.imeSessionStart("en", true)), sink.flushed)
    }

    @Test fun flushNowSendsImmediatelyAndCancelsSchedule() = runTest {
        val sink = Recorder()
        val relay = relay(sink)
        relay.log(EventCatalog.privacyPrivateAppAdd())
        relay.flushNow()
        assertEquals(1, sink.flushed.size)
        advanceTimeBy(61_000)
        assertEquals(1, sink.flushed.size)
    }
}
