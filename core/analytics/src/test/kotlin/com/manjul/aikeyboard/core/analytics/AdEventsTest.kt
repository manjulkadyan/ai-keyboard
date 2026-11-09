/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.analytics

import kotlin.test.Test
import kotlin.test.assertEquals

/** S4 Task 7: `ad_paid_event` carries closed values and the revenue in micros, nothing else. */
class AdEventsTest {
    @Test fun `paid event carries the five params`() {
        val e = EventCatalog.adPaidEvent("banner_home", AdFormatParam.BANNER, 1_500L, "USD", AdPrecisionParam.PRECISE)
        assertEquals("ad_paid_event", e.name)
        assertEquals(
            mapOf(
                "placement" to "banner_home", "format" to "banner", "value_micros" to 1_500L, "currency" to "USD",
                "precision" to "precise",
            ),
            e.params,
        )
    }

    @Test fun `a placement that is not snake case or a bad currency becomes other`() {
        val e = EventCatalog.adPaidEvent("ca-app-pub-1/2", AdFormatParam.NATIVE, 1, "dollars", AdPrecisionParam.UNKNOWN)
        assertEquals("other", e.params["placement"])
        assertEquals("other", e.params["currency"])
    }

    @Test fun `enum wire values`() {
        assertEquals(
            listOf("app_open", "interstitial", "rewarded", "banner", "native"),
            AdFormatParam.entries.map { it.value },
        )
        assertEquals(
            listOf("unknown", "estimated", "publisher_provided", "precise"),
            AdPrecisionParam.entries.map { it.value },
        )
    }
}
