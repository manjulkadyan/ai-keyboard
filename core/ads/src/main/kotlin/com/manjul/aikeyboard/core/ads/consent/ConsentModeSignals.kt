/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ads.consent

import android.content.Context

/**
 * The four Consent Mode v2 signals for Firebase Analytics (TRD §4.11), from the UMP result and the IAB TCF v2 strings
 * that UMP writes to the default shared preferences.
 *
 * Under GDPR the signals follow Google's TCF mapping, and all of them need consent for Google (vendor 755):
 * - `ad_storage` and `analytics_storage`: purpose 1 (store or access information on a device)
 * - `ad_user_data`: purposes 1 and 7 (measure ad performance)
 * - `ad_personalization`: purposes 3 and 4 (personalised ads profile and selection)
 *
 * Outside GDPR every signal is granted once UMP allows ad requests. Without ad requests every signal is denied.
 */
data class ConsentModeSignals(
    val adStorage: Boolean,
    val adUserData: Boolean,
    val adPersonalization: Boolean,
    val analyticsStorage: Boolean,
) {
    companion object {
        val GRANTED = ConsentModeSignals(adStorage = true, adUserData = true, adPersonalization = true, analyticsStorage = true)
        val DENIED = ConsentModeSignals(adStorage = false, adUserData = false, adPersonalization = false, analyticsStorage = false)

        private const val GOOGLE_VENDOR_ID = 755
        private const val TCF_GDPR_APPLIES = "IABTCF_gdprApplies"
        private const val TCF_PURPOSE_CONSENTS = "IABTCF_PurposeConsents"
        private const val TCF_VENDOR_CONSENTS = "IABTCF_VendorConsents"

        fun from(canRequestAds: Boolean, gdprApplies: Int?, purposeConsents: String?, vendorConsents: String?): ConsentModeSignals {
            if (!canRequestAds) return DENIED
            if (gdprApplies != 1) return GRANTED
            if (purposeConsents == null || !vendorConsents.hasBit(GOOGLE_VENDOR_ID)) return DENIED
            val purpose = { id: Int -> purposeConsents.hasBit(id) }
            return ConsentModeSignals(
                adStorage = purpose(1),
                adUserData = purpose(1) && purpose(7),
                adPersonalization = purpose(3) && purpose(4),
                analyticsStorage = purpose(1),
            )
        }

        /** Reads the TCF strings UMP stored. A missing or mistyped value counts as no consent. */
        fun read(context: Context, canRequestAds: Boolean): ConsentModeSignals {
            val prefs = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
            val gdprApplies = runCatching { if (prefs.contains(TCF_GDPR_APPLIES)) prefs.getInt(TCF_GDPR_APPLIES, 0) else null }
                .getOrDefault(1)
            val purposes = runCatching { prefs.getString(TCF_PURPOSE_CONSENTS, null) }.getOrNull()
            val vendors = runCatching { prefs.getString(TCF_VENDOR_CONSENTS, null) }.getOrNull()
            return from(canRequestAds, gdprApplies, purposes, vendors)
        }

        /** TCF bit strings are 1-based: id N is the character at index N - 1. */
        private fun String?.hasBit(id: Int): Boolean = this != null && length >= id && this[id - 1] == '1'
    }
}
