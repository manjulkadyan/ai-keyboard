/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.app.ads

import com.manjul.aikeyboard.core.datastore.FeatureSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** I-1: the UMP result's `analytics_storage` sets or clears the stored Analytics consent that gates Firebase. */
class AnalyticsConsentTest {
    @Test
    fun `defaults allow no analytics`() {
        assertFalse(FeatureSettings().analyticsAllowed)
    }

    @Test
    fun `a grant stores the first grant time and allows analytics`() {
        val granted = AdsAppGraph.analyticsConsentFrom(FeatureSettings(), analyticsStorage = true, now = 100L)
        assertEquals(100L, granted.analyticsConsentAt)
        assertTrue(granted.analyticsAllowed)
        assertEquals(100L, AdsAppGraph.analyticsConsentFrom(granted, analyticsStorage = true, now = 200L).analyticsConsentAt)
    }

    @Test
    fun `a denial clears the consent`() {
        val granted = FeatureSettings(analyticsConsentAt = 100L)
        val denied = AdsAppGraph.analyticsConsentFrom(granted, analyticsStorage = false, now = 200L)
        assertEquals(0L, denied.analyticsConsentAt)
        assertFalse(denied.analyticsAllowed)
    }

    @Test
    fun `the opt-out wins over consent`() {
        assertFalse(FeatureSettings(analyticsConsentAt = 100L, analyticsOptOut = true).analyticsAllowed)
    }
}
