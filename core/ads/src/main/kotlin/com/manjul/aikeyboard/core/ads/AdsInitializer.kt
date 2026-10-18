/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ads

import android.app.Activity
import androidx.annotation.MainThread
import com.manjul.aikeyboard.core.ads.consent.ConsentError
import com.manjul.aikeyboard.core.ads.consent.ConsentManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The ad SDK behind the consent gate. Sprint 0 ships [NoAdSdk]; the Google Mobile Ads implementation
 * (`MobileAds.initialize`) arrives with the placements in Sprint 4.
 */
interface AdSdk {
    /** Called at most once per process, only after consent allows ad requests. */
    @MainThread
    fun initialize(activity: Activity, onComplete: () -> Unit)
}

/** No ad SDK linked yet: initialisation completes immediately and loads nothing. */
object NoAdSdk : AdSdk {
    override fun initialize(activity: Activity, onComplete: () -> Unit) = onComplete()
}

data class AdsStatus(
    val phase: Phase = Phase.NOT_STARTED,
    /** Settings shows "Privacy options" while this is true (TRD §4.11). */
    val privacyOptionsRequired: Boolean = false,
    val lastConsentError: ConsentError? = null,
) {
    enum class Phase {
        NOT_STARTED,
        GATHERING_CONSENT,
        /** Consent does not allow ad requests (yet). The next cold start asks again. */
        CONSENT_NOT_GRANTED,
        /** The user is entitled to an ad-free app (TRD §4.11 "Pro"); the SDK is not initialised. */
        ADS_DISABLED,
        INITIALIZING_SDK,
        READY,
        /** The SDK threw during initialisation; ads stay off for this process. */
        SDK_FAILED,
    }
}

/**
 * Entry point of the ads stack. Nothing here runs at process start: [initialize] takes an
 * [Activity] and must be called from the main Activity on every cold start (TRD §2.3, §4.11).
 * It is never called from `Application.onCreate` or from the IME (TRD §11.1).
 *
 * Order: UMP consent first, then the ad SDK, and only when `canRequestAds()` is true and the user
 * is not entitled to an ad-free app.
 *
 * @param adsDisabled Entitlement check; true for Pro users. Read each time consent resolves.
 */
class AdsInitializer(
    private val consentManager: ConsentManager,
    private val adSdk: AdSdk = NoAdSdk,
    private val adsDisabled: () -> Boolean = { false },
) {
    private val _status = MutableStateFlow(AdsStatus())
    val status: StateFlow<AdsStatus> = _status.asStateFlow()

    private var sdkStarted = false
    private var sdkPhase = AdsStatus.Phase.NOT_STARTED

    @MainThread
    fun initialize(activity: Activity) {
        if (_status.value.phase == AdsStatus.Phase.GATHERING_CONSENT) return
        _status.update { it.copy(phase = AdsStatus.Phase.GATHERING_CONSENT) }
        consentManager.gatherConsent(activity) { result ->
            _status.update {
                it.copy(privacyOptionsRequired = result.privacyOptionsRequired, lastConsentError = result.error)
            }
            when {
                !result.canRequestAds -> setPhase(AdsStatus.Phase.CONSENT_NOT_GRANTED)
                adsDisabled() -> setPhase(AdsStatus.Phase.ADS_DISABLED)
                else -> startSdk(activity)
            }
        }
    }

    private fun startSdk(activity: Activity) {
        if (sdkStarted) {
            // Already initialised (or initialising) in this process; a later cold start of the
            // Activity only refreshes consent.
            setPhase(sdkPhase)
            return
        }
        sdkStarted = true
        sdkPhase = AdsStatus.Phase.INITIALIZING_SDK
        setPhase(sdkPhase)
        // TRD §11.1 1.3: an SDK failure must not crash the shared app/IME process.
        runCatching {
            adSdk.initialize(activity) {
                sdkPhase = AdsStatus.Phase.READY
                setPhase(sdkPhase)
            }
        }.onFailure {
            sdkPhase = AdsStatus.Phase.SDK_FAILED
            setPhase(sdkPhase)
        }
    }

    private fun setPhase(phase: AdsStatus.Phase) = _status.update { it.copy(phase = phase) }
}
