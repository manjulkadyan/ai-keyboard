/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.app.ads

import com.google.android.gms.ads.AdValue
import com.manjul.aikeyboard.core.ads.placement.AdFormat
import com.manjul.aikeyboard.core.ads.placement.AdPlacements
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** S4 Task 7: one `ad_paid_event` per paid callback, with the 5 registered params. */
class PaidEventLoggerTest {
    private val logged = mutableListOf<AnalyticsEvent>()
    private val logger = PaidEventLogger(logged::add)

    // AdValue has no public constructor; zza(precision, currency, micros) is its public static factory in 25.5.0.
    private fun value(precision: Int, currency: String, micros: Long) = AdValue.zza(precision, currency, micros)

    @Test
    fun `the event has the 5 params and value_micros is a Long`() {
        logger.log(AdPlacements.NATIVE_TRANSLATOR, AdFormat.NATIVE, value(AdValue.PrecisionType.PRECISE, "USD", 1_250L))
        val e = logged.single()
        assertEquals("ad_paid_event", e.name)
        assertEquals(
            mapOf(
                "placement" to "native_translator",
                "format" to "native",
                "value_micros" to 1_250L,
                "currency" to "USD",
                "precision" to "precise",
            ),
            e.params,
        )
        assertIs<Long>(e.params["value_micros"])
    }

    @Test
    fun `formats and precision types map to closed values`() {
        logger.log(AdPlacements.APP_OPEN, AdFormat.APP_OPEN, value(AdValue.PrecisionType.UNKNOWN, "EUR", 0))
        logger.log(AdPlacements.INTER_FEATURE_EXIT, AdFormat.INTERSTITIAL, value(AdValue.PrecisionType.ESTIMATED, "INR", 9))
        logger.log(AdPlacements.BANNER_HOME, AdFormat.BANNER, value(AdValue.PrecisionType.PUBLISHER_PROVIDED, "JPY", 1))
        logger.log(AdPlacements.REWARDED_GAME_HINT, AdFormat.REWARDED, value(99, "GBP", 2))
        assertEquals(listOf("app_open", "interstitial", "banner", "rewarded"), logged.map { it.params["format"] })
        assertEquals(
            listOf("unknown", "estimated", "publisher_provided", "unknown"),
            logged.map { it.params["precision"] },
        )
    }

    @Test
    fun `a currency that is not ISO 4217 becomes other`() {
        logger.log(AdPlacements.BANNER_HOME, AdFormat.BANNER, value(AdValue.PrecisionType.PRECISE, "usd!", 5))
        assertEquals("other", logged.single().params["currency"])
    }

    @Test
    fun `a failing sink never throws into the ad SDK`() {
        val failing = PaidEventLogger { error("sink down") }
        failing.log(AdPlacements.BANNER_HOME, AdFormat.BANNER, value(AdValue.PrecisionType.PRECISE, "USD", 5))
    }
}
