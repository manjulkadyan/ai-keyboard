/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.app.ads

import android.app.Activity
import android.app.Application
import androidx.annotation.MainThread
import com.manjul.aikeyboard.core.ads.AdsInitializer
import com.manjul.aikeyboard.core.ads.AdsStatus
import com.manjul.aikeyboard.core.ads.consent.ConsentModeSignals
import com.manjul.aikeyboard.core.ads.consent.ConsentResult
import com.manjul.aikeyboard.core.ads.consent.UmpConsentManager
import com.manjul.aikeyboard.core.ads.gma.GmaAdSdk
import com.manjul.aikeyboard.core.ads.gma.GoogleMobileAdsFacade
import com.manjul.aikeyboard.core.config.ConfigKeys
import com.manjul.aikeyboard.featureSettingsStore
import com.manjul.aikeyboard.firebaseGraph
import com.manjul.aikeyboard.fork.billing.proGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow

/**
 * The app-process ads stack (S4 Task 6): UMP consent, then the GMA SDK. One per process, created on the first
 * [get] from the main Activity. It is not a field on `FlorisApplication`, which the IME loads: only Activity code
 * reaches this package (`ImeAdIsolationTest`), so an IME-only process never loads it or any GMA class.
 */
class AdsAppGraph(private val app: Application) {
    private val settings by app.featureSettingsStore()
    private val firebase by app.firebaseGraph()
    private val isPro by lazy { proGate(settings) }

    val initializer: AdsInitializer = AdsInitializer(
        consentManager = UmpConsentManager(),
        adSdk = GmaAdSdk(GoogleMobileAdsFacade, Dispatchers.IO, firebase.reporter),
        // Read again when consent resolves: a purchase or a kill switch during the form keeps the SDK off.
        adsDisabled = { !adsAllowed() },
        onConsent = ::onConsent,
    )
    val status: StateFlow<AdsStatus> = initializer.status

    /**
     * Called by the main Activity on each cold start. Starts consent and the SDK only when onboarding is done, the
     * user is not Pro and the `ads_enabled_global` kill switch is on. Otherwise nothing of UMP or GMA runs.
     */
    @MainThread
    fun onAppStart(activity: Activity, onboardingDone: Boolean) {
        if (!onboardingDone || !adsAllowed()) return
        initializer.initialize(activity)
    }

    private fun adsAllowed(): Boolean = !isPro() && firebase.remoteConfig.bool(ConfigKeys.ADS_ENABLED_GLOBAL)

    /** Consent Mode v2 for Analytics from the UMP result and the TCF strings UMP stored (TRD §4.11). */
    private fun onConsent(result: ConsentResult) {
        val signals = ConsentModeSignals.read(app, result.canRequestAds)
        firebase.setConsentMode(
            adStorage = signals.adStorage,
            adUserData = signals.adUserData,
            adPersonalization = signals.adPersonalization,
            analyticsStorage = signals.analyticsStorage,
        )
    }

    companion object {
        @Volatile
        private var instance: AdsAppGraph? = null

        fun get(app: Application): AdsAppGraph =
            instance ?: synchronized(this) { instance ?: AdsAppGraph(app).also { instance = it } }
    }
}
