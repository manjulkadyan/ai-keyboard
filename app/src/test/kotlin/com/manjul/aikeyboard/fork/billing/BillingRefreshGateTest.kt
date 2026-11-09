/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.billing

import com.manjul.aikeyboard.core.datastore.FeatureSettings
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** App start and resume reach kb-api only after AI consent or a past kb-api entitlement answer. */
class BillingRefreshGateTest {
    @Test
    fun `a fresh install does not call kb-api on start or resume`() {
        assertFalse(BillingAppGraph.mayAutoRefresh(FeatureSettings()))
    }

    @Test
    fun `ai consent allows the refresh`() {
        assertTrue(BillingAppGraph.mayAutoRefresh(FeatureSettings(consentAiAt = 5L)))
    }

    @Test
    fun `a past entitlement answer allows the refresh`() {
        assertTrue(BillingAppGraph.mayAutoRefresh(FeatureSettings(proCheckedAt = 5L)))
    }

    @Test
    fun `resume right after a cold start is skipped`() {
        val gate = ResumeAfterColdStart(throttleMs = 100)
        gate.coldStarted(nowMs = 1_000)
        assertFalse(gate.mayResume(nowMs = 1_050))
        assertTrue(gate.mayResume(nowMs = 1_100))
    }

    @Test
    fun `resume with no cold start in this process is allowed`() {
        assertTrue(ResumeAfterColdStart(throttleMs = 100).mayResume(nowMs = 0))
    }
}
