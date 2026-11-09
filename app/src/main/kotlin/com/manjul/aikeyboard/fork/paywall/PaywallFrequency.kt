/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.paywall

import com.manjul.aikeyboard.core.datastore.FeatureSettings
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * How often the paywall may show by itself (PRD §10.4). Only device-local timestamps and counts are read and
 * written ([FeatureSettings.paywallOnboardingShownAt], [FeatureSettings.winBackShownCount],
 * [FeatureSettings.winBackLastShownAt]); nothing leaves the device.
 *
 * - T1 onboarding (O6): once ever, only after `ime_selected`, and only while Remote Config
 *   `paywall_onboarding_enabled` is on (OD-05).
 * - T8 win-back: a Home card on day 3 and day 10 after `firstOpenAt`, never for Pro, never twice on one day, at
 *   most 2 in a lifetime.
 *
 * `show*` only reads. `claim*` decides and records the showing in one step, so two callers never both show it.
 * This file must not import `:core:billing`: onboarding uses it.
 */
class PaywallFrequency(private val settings: FeatureSettingsStore, private val clock: () -> Long) {
    private val mutex = Mutex()

    fun showOnboarding(imeSelected: Boolean, rcEnabled: Boolean): Boolean =
        onboardingAllowed(settings.current, imeSelected, rcEnabled)

    /** True once: the caller shows O6 now. The showing is stored before this returns. */
    suspend fun claimOnboarding(imeSelected: Boolean, rcEnabled: Boolean): Boolean = mutex.withLock {
        if (!onboardingAllowed(settings.flow.first(), imeSelected, rcEnabled)) return@withLock false
        val now = clock()
        settings.update { it.copy(paywallOnboardingShownAt = now.coerceAtLeast(1L)) }
        true
    }

    fun showWinBack(isPro: Boolean): Boolean = winBackAllowed(settings.current, isPro, clock())

    /** True when the card shows now. Counts the showing before this returns. */
    suspend fun claimWinBack(isPro: Boolean): Boolean = mutex.withLock {
        val now = clock()
        if (!winBackAllowed(settings.flow.first(), isPro, now)) return@withLock false
        settings.update { it.copy(winBackShownCount = it.winBackShownCount + 1, winBackLastShownAt = now) }
        true
    }

    private fun onboardingAllowed(s: FeatureSettings, imeSelected: Boolean, rcEnabled: Boolean) =
        imeSelected && rcEnabled && s.paywallOnboardingShownAt == 0L

    private fun winBackAllowed(s: FeatureSettings, isPro: Boolean, now: Long): Boolean {
        if (isPro || s.firstOpenAt <= 0L || now < s.firstOpenAt) return false
        if (s.winBackShownCount >= WIN_BACK_LIFETIME_MAX) return false
        val day = dayOf(s.firstOpenAt, now)
        if (day !in WIN_BACK_DAYS) return false
        val last = s.winBackLastShownAt
        return last <= 0L || last < s.firstOpenAt || dayOf(s.firstOpenAt, last) != day
    }

    private fun dayOf(firstOpenAt: Long, at: Long) = (at - firstOpenAt) / DAY_MS

    companion object {
        const val DAY_MS = 86_400_000L
        const val WIN_BACK_LIFETIME_MAX = 2
        val WIN_BACK_DAYS = setOf(3L, 10L)
    }
}
