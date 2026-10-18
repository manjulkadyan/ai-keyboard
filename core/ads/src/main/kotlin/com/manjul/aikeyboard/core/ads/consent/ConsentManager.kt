/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ads.consent

import android.app.Activity
import androidx.annotation.MainThread

/**
 * Consent gate for ads (TRD §4.11 "Consent"). The only implementation for production is
 * [UmpConsentManager]; tests use a fake.
 *
 * Every method takes an [Activity]: consent is never requested at process start, from the
 * `Application`, or from the IME (TRD §2.3, §11.1).
 */
interface ConsentManager {
    /**
     * Refreshes the consent information and shows the consent form when one is required.
     * Call it on **every** cold start of the main Activity (TRD §4.11).
     *
     * [onResult] is called exactly once per call, on the main thread, after the form (if any) is closed
     * or the request failed. A failed request still reports the last known [ConsentResult.canRequestAds],
     * so a user who consented in an earlier session keeps getting ads when offline.
     */
    @MainThread
    fun gatherConsent(activity: Activity, onResult: (ConsentResult) -> Unit)

    /** Last known state, from the SDK's persisted consent. Does not make a network request. */
    fun canRequestAds(activity: Activity): Boolean

    /** True when Settings must show a "Privacy options" entry (TRD §4.11). */
    fun isPrivacyOptionsRequired(activity: Activity): Boolean

    /** Opens the privacy options form from the Settings entry. [onDismissed] gets the error, if any. */
    @MainThread
    fun showPrivacyOptionsForm(activity: Activity, onDismissed: (ConsentError?) -> Unit)
}

/** Outcome of [ConsentManager.gatherConsent]. */
data class ConsentResult(
    val canRequestAds: Boolean,
    val privacyOptionsRequired: Boolean,
    /** Set when the info update or the form failed; the other fields are then the last known state. */
    val error: ConsentError? = null,
)

/** A UMP error, copied out so callers do not depend on UMP types. */
data class ConsentError(val code: Int, val message: String)
