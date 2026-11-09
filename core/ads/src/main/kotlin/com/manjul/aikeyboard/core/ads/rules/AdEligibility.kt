/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ads.rules

import com.manjul.aikeyboard.core.ads.cap.AdCapDecision
import com.manjul.aikeyboard.core.common.entry.EntrySource

/** A navigation step in the app, as `NavTransitionReporter` classifies it. */
enum class NavTransition {
    /** Back from a feature screen (Translator, History, a theme screen) to Home. */
    FEATURE_TO_HOME,
    HOME_TO_FEATURE,

    /** The user leaves the app. */
    EXIT,
    OTHER,
}

/** Everything the rules read, captured at one moment. [cap] is `AdCapPolicy.evaluate` for the placement at [nowMs]. */
data class AdContext(
    val isPro: Boolean,
    val adsReady: Boolean,
    val sessionIndex: Int,
    val entry: EntrySource,
    val onSplashOrOnboarding: Boolean,
    val returningFromExternalFlow: Boolean,
    val featureUsesSinceInterstitial: Int,
    val lastInterstitialAtMs: Long,
    val nowMs: Long,
    val cap: AdCapDecision,
)

/**
 * The show rules of S4 Task 7 (TRD §4.11, PRD FR-27). Pure: every input is in [AdContext], plus the placement's
 * unit id (null or empty means the placement is off). There is no rule for an action: no caller asks for an
 * interstitial from Translate, Mic, Copy, Apply theme or Purchase, only [NavTransition.FEATURE_TO_HOME] can.
 */
object AdEligibility {
    /** Between an interstitial and an app-open ad. */
    const val APP_OPEN_AFTER_INTERSTITIAL_MS: Long = 30_000

    /** Completed feature uses before an interstitial (plan decision 14). */
    const val FEATURE_USES_FOR_INTERSTITIAL: Int = 2

    private val appOpenEntries = setOf(EntrySource.LAUNCHER, EntrySource.RESUME)
    private val noInterstitialEntries = setOf(EntrySource.PROCESS_TEXT, EntrySource.SEND, EntrySource.NOTIFICATION)

    fun appOpen(c: AdContext, unitId: String?): Boolean =
        common(c, unitId) &&
            c.entry in appOpenEntries &&
            !c.onSplashOrOnboarding &&
            !c.returningFromExternalFlow &&
            c.nowMs - c.lastInterstitialAtMs >= APP_OPEN_AFTER_INTERSTITIAL_MS &&
            c.cap == AdCapDecision.Allowed

    fun interstitial(c: AdContext, t: NavTransition, unitId: String?): Boolean =
        common(c, unitId) &&
            t == NavTransition.FEATURE_TO_HOME &&
            c.featureUsesSinceInterstitial >= FEATURE_USES_FOR_INTERSTITIAL &&
            c.entry !in noInterstitialEntries &&
            c.cap == AdCapDecision.Allowed

    fun bannerOrNative(c: AdContext, unitId: String?): Boolean = common(c, unitId)

    /** Every format: not Pro, the SDK is ready, not the first session, and the placement has a unit id. */
    private fun common(c: AdContext, unitId: String?): Boolean =
        !c.isPro && c.adsReady && c.sessionIndex > 1 && !unitId.isNullOrBlank()
}
