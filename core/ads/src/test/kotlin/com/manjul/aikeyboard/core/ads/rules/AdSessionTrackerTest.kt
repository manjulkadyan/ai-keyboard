/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ads.rules

import com.manjul.aikeyboard.core.ads.cap.AdsState
import com.manjul.aikeyboard.core.ads.cap.DataStoreAdCapStateStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

/** Plan decision 15: a session starts at a cold start, or on a return after at least 30 min in the background. */
class AdSessionTrackerTest {
    private var now = 1_800_000_000_000L
    private val dataStore = InMemoryDataStore(AdsState())
    private val store = DataStoreAdCapStateStore(dataStore)
    private val tracker = AdSessionTracker(store) { now }

    @Test
    fun `a cold start increments the session`() = runTest {
        tracker.onAppStart(coldStart = true)
        assertEquals(1, tracker.sessionIndex)
        tracker.onAppStart(coldStart = true)
        assertEquals(2, tracker.sessionIndex)
        assertEquals(2, dataStore.value.session.sessionCount)
    }

    @Test
    fun `the session count survives a new tracker`() = runTest {
        tracker.onAppStart(coldStart = true)
        val next = AdSessionTracker(store) { now }
        next.onAppStart(coldStart = true)
        assertEquals(2, next.sessionIndex)
    }

    @Test
    fun `a resume after 29 min does not start a session`() = runTest {
        tracker.onAppStart(coldStart = true)
        tracker.onBackground()
        now += 29.minutes.inWholeMilliseconds
        assertEquals(false, tracker.onAppStart(coldStart = false))
        assertEquals(1, tracker.sessionIndex)
    }

    @Test
    fun `a resume after 30 min starts a session`() = runTest {
        tracker.onAppStart(coldStart = true)
        tracker.onBackground()
        now += 30.minutes.inWholeMilliseconds
        assertEquals(true, tracker.onAppStart(coldStart = false))
        assertEquals(2, tracker.sessionIndex)
    }

    @Test
    fun `a resume without a recorded background does not start a session`() = runTest {
        tracker.onAppStart(coldStart = true)
        now += 60.minutes.inWholeMilliseconds
        tracker.onAppStart(coldStart = false)
        assertEquals(1, tracker.sessionIndex)
    }

    @Test
    fun `feature uses count and reset on an interstitial`() = runTest {
        tracker.onAppStart(coldStart = true)
        tracker.onFeatureUse()
        tracker.onFeatureUse()
        assertEquals(2, tracker.featureUsesSinceInterstitial)
        now += 1_000
        tracker.onInterstitialShown()
        assertEquals(0, tracker.featureUsesSinceInterstitial)
        assertEquals(now, tracker.lastInterstitialAtMs)
        assertEquals(now, dataStore.value.session.lastInterstitialAtMs)
    }

    @Test
    fun `feature uses reset on a new session`() = runTest {
        tracker.onAppStart(coldStart = true)
        tracker.onFeatureUse()
        tracker.onFeatureUse()
        tracker.onBackground()
        now += 30.minutes.inWholeMilliseconds
        tracker.onAppStart(coldStart = false)
        assertEquals(0, tracker.featureUsesSinceInterstitial)
    }

    @Test
    fun `feature uses stay across a short background`() = runTest {
        tracker.onAppStart(coldStart = true)
        tracker.onFeatureUse()
        tracker.onBackground()
        now += 1.minutes.inWholeMilliseconds
        tracker.onAppStart(coldStart = false)
        assertEquals(1, tracker.featureUsesSinceInterstitial)
    }

    @Test
    fun `an app open show is recorded`() = runTest {
        tracker.onAppOpenShown()
        assertEquals(now, dataStore.value.session.lastAppOpenAtMs)
    }
}
