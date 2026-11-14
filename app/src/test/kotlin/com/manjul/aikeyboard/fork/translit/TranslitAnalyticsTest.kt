/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translit

import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.RateSampler
import com.manjul.aikeyboard.core.translit.TranslitLang
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TranslitAnalyticsTest {
    private val events = mutableListOf<AnalyticsEvent>()

    @Test fun aFiringSamplerSendsLangAndSourceOnly() {
        val analytics = TranslitAnalytics({ events += it }, RateSampler(1.0) { 0.0 })
        analytics.onCommit(TranslitLang.UR, latin = false)
        analytics.onCommit(TranslitLang.HI, latin = true)
        assertEquals("translit_commit", events.first().name)
        assertEquals(mapOf("lang" to "ur", "source" to "candidate"), events[0].params)
        assertEquals(mapOf("lang" to "hi", "source" to "latin"), events[1].params)
        // No text and no length: only the two registered keys, and only fixed values.
        assertTrue(events.all { it.params.values.all { v -> v in setOf("ur", "hi", "candidate", "latin") } })
    }

    @Test fun aSilentSamplerSendsNothing() {
        val analytics = TranslitAnalytics({ events += it }, RateSampler(0.0) { 0.5 })
        analytics.onCommit(TranslitLang.UR, latin = false)
        assertTrue(events.isEmpty())
    }

    @Test fun theRateIsOnePercent() {
        assertEquals(0.01, TranslitAnalytics.SAMPLE_RATE)
    }
}
