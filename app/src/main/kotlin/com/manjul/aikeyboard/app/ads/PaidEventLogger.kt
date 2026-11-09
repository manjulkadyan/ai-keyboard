/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.app.ads

import com.google.android.gms.ads.AdValue
import com.manjul.aikeyboard.core.ads.placement.AdFormat
import com.manjul.aikeyboard.core.ads.placement.AdPlacementId
import com.manjul.aikeyboard.core.analytics.AdFormatParam
import com.manjul.aikeyboard.core.analytics.AdPrecisionParam
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.EventCatalog

/**
 * Logs one `ad_paid_event` per GMA paid callback (impression-level revenue). The event holds the placement, the
 * format, the value in micros, the currency and the precision only: no ad unit id, no ad content, no identifier.
 * A failing sink never throws into the ad SDK.
 */
class PaidEventLogger(private val log: (AnalyticsEvent) -> Unit) {
    fun log(placement: AdPlacementId, format: AdFormat, value: AdValue) {
        try {
            log(
                EventCatalog.adPaidEvent(
                    placement = placement.value,
                    format = format.param(),
                    valueMicros = value.valueMicros,
                    currency = value.currencyCode,
                    precision = precision(value.precisionType),
                ),
            )
        } catch (_: Exception) {
            // Revenue logging is best effort.
        }
    }

    private fun AdFormat.param(): AdFormatParam = when (this) {
        AdFormat.APP_OPEN -> AdFormatParam.APP_OPEN
        AdFormat.INTERSTITIAL -> AdFormatParam.INTERSTITIAL
        AdFormat.REWARDED -> AdFormatParam.REWARDED
        AdFormat.BANNER -> AdFormatParam.BANNER
        AdFormat.NATIVE -> AdFormatParam.NATIVE
    }

    private fun precision(type: Int): AdPrecisionParam = when (type) {
        AdValue.PrecisionType.ESTIMATED -> AdPrecisionParam.ESTIMATED
        AdValue.PrecisionType.PUBLISHER_PROVIDED -> AdPrecisionParam.PUBLISHER_PROVIDED
        AdValue.PrecisionType.PRECISE -> AdPrecisionParam.PRECISE
        else -> AdPrecisionParam.UNKNOWN
    }
}
