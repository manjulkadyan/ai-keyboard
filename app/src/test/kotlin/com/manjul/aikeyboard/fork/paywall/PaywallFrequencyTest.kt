/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.paywall

import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PaywallFrequencyTest {
    private val store = FeatureSettingsStore(RuntimeEnvironment.getApplication(), UnconfinedTestDispatcher())
    private val first = 1_000_000_000L
    private var now = first
    private val frequency = PaywallFrequency(store) { now }

    private fun day(n: Int, hours: Int = 0) = first + n * DAY_MS + hours * HOUR_MS

    @Test
    fun onboardingShowsOnceOnlyAfterImeSelectedAndWithRcOn() = runTest {
        assertFalse(frequency.showOnboarding(imeSelected = false, rcEnabled = true))
        assertFalse(frequency.showOnboarding(imeSelected = true, rcEnabled = false))
        assertTrue(frequency.showOnboarding(imeSelected = true, rcEnabled = true))
        assertTrue(frequency.claimOnboarding(imeSelected = true, rcEnabled = true))
        assertEquals(now, store.flow.first().paywallOnboardingShownAt)
        assertFalse(frequency.showOnboarding(imeSelected = true, rcEnabled = true))
        assertFalse(frequency.claimOnboarding(imeSelected = true, rcEnabled = true))
    }

    @Test
    fun claimDoesNotMarkWhenItDoesNotShow() = runTest {
        assertFalse(frequency.claimOnboarding(imeSelected = true, rcEnabled = false))
        assertEquals(0L, store.flow.first().paywallOnboardingShownAt)
    }

    @Test
    fun winBackShowsOnDay3AndDay10Only() = runTest {
        store.update { it.copy(firstOpenAt = first) }
        for (d in listOf(0, 1, 2, 4, 9, 11, 30)) {
            now = day(d, hours = 5)
            assertFalse(frequency.showWinBack(isPro = false), "day $d")
        }
        now = day(3, hours = 5)
        assertTrue(frequency.showWinBack(isPro = false))
        now = day(10)
        assertTrue(frequency.showWinBack(isPro = false))
    }

    @Test
    fun winBackNeverForPro() = runTest {
        store.update { it.copy(firstOpenAt = first) }
        now = day(3)
        assertFalse(frequency.showWinBack(isPro = true))
    }

    @Test
    fun winBackNeverTwiceOnOneDayAndNeverAThirdTime() = runTest {
        store.update { it.copy(firstOpenAt = first) }
        now = day(3, hours = 1)
        assertTrue(frequency.claimWinBack(isPro = false))
        now = day(3, hours = 20)
        assertFalse(frequency.showWinBack(isPro = false))
        now = day(10, hours = 2)
        assertTrue(frequency.claimWinBack(isPro = false))
        assertEquals(2, store.flow.first().winBackShownCount)
        assertEquals(now, store.flow.first().winBackLastShownAt)
        // A third time never shows, even on a qualifying day (a reset clock or a stale firstOpenAt).
        store.update { it.copy(firstOpenAt = now - 3 * DAY_MS) }
        assertFalse(frequency.showWinBack(isPro = false))
    }

    @Test
    fun winBackNeedsAFirstOpen() = runTest {
        now = day(3)
        assertFalse(frequency.showWinBack(isPro = false))
    }

    private companion object {
        const val HOUR_MS = 3_600_000L
        const val DAY_MS = 24 * HOUR_MS
    }
}
