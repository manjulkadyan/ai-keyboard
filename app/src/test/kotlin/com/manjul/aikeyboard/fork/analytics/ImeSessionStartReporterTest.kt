/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.analytics

import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.AnalyticsSink
import com.manjul.aikeyboard.core.analytics.BatchingRelay
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import com.manjul.aikeyboard.ime.policy.SensitivityReason
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

/** R36: `ime_session_start` is sent from onStartInputView, sampled 1 per 10 min, and passes a minimal policy. */
@OptIn(ExperimentalCoroutinesApi::class)
class ImeSessionStartReporterTest {
    private val password = PolicyDecision.Locked.copy(reasons = setOf(SensitivityReason.PASSWORD))

    private class Recorder : AnalyticsSink {
        val flushed = mutableListOf<AnalyticsEvent>()
        override fun log(event: AnalyticsEvent) { flushed += event }
    }

    /** The service's wiring: the reporter logs into the real relay, which reads the same live decision. */
    private class Rig(scope: TestScope) {
        var decision: PolicyDecision = PolicyDecision.Unrestricted
        val sink = Recorder()
        val relay = BatchingRelay(sink, { scope.currentTime }, scope, optOut = { false }, policy = { decision })
        val reporter = ImeSessionStartReporter(clock = { scope.currentTime }, log = relay::log)

        fun show(next: PolicyDecision, subtype: String = "en-US") {
            decision = next
            reporter.onStartInputView(next, subtype)
        }
    }

    @Test fun normalFieldLogsANonSensitiveSessionStart() = runTest {
        val r = Rig(this)
        r.show(PolicyDecision.Unrestricted)
        advanceTimeBy(61_000)
        assertEquals(listOf(EventCatalog.imeSessionStart("en-US", false)), r.sink.flushed)
    }

    @Test fun sensitiveFieldSessionStartPassesTheMinimalPolicy() = runTest {
        val r = Rig(this)
        r.show(password, subtype = "hi-IN")
        advanceTimeBy(61_000)
        assertEquals(listOf(EventCatalog.imeSessionStart("hi-IN", true)), r.sink.flushed)
    }

    @Test fun sampledOncePerTenMinutes() = runTest {
        val r = Rig(this)
        r.show(PolicyDecision.Unrestricted)
        advanceTimeBy(9 * 60_000L)
        r.show(password)
        advanceTimeBy(60_000L) // t = 10 min
        r.show(PolicyDecision.Unrestricted)
        advanceTimeBy(61_000)
        assertEquals(
            listOf(EventCatalog.imeSessionStart("en-US", false), EventCatalog.imeSessionStart("en-US", false)),
            r.sink.flushed,
        )
    }
}
