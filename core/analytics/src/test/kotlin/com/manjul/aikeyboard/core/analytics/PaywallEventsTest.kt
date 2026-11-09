/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.analytics

import kotlin.test.Test
import kotlin.test.assertEquals

/** PRD §12 paywall events: a closed trigger value and a time bucket, nothing else. */
class PaywallEventsTest {
    @Test fun `paywall view carries the trigger`() {
        val e = EventCatalog.paywallView(PaywallTriggerParam.AI_QUOTA)
        assertEquals("paywall_view", e.name)
        assertEquals(mapOf("trigger" to "ai_quota"), e.params)
    }

    @Test fun `paywall close carries the trigger and a seconds bucket`() {
        val e = EventCatalog.paywallClose(PaywallTriggerParam.ONBOARDING, 12)
        assertEquals("paywall_close", e.name)
        assertEquals(mapOf("trigger" to "onboarding", "seconds_bucket" to "5-15"), e.params)
        assertEquals("0-5", EventCatalog.paywallClose(PaywallTriggerParam.WIN_BACK, -3).params["seconds_bucket"])
    }

    @Test fun `trigger wire values match PRD section 10_4`() {
        assertEquals(
            listOf(
                "onboarding", "ai_quota", "premium_theme", "remove_ads", "cloud_quality", "premium_sticker",
                "auto_polish", "win_back",
            ),
            PaywallTriggerParam.entries.map { it.value },
        )
    }
}
