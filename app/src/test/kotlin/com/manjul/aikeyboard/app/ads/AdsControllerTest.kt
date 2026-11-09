/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.app.ads

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.datastore.core.DataStore
import com.manjul.aikeyboard.core.ads.cap.AdsState
import com.manjul.aikeyboard.core.ads.cap.AdSessionState
import com.manjul.aikeyboard.core.ads.cap.DataStoreAdCapStateStore
import com.manjul.aikeyboard.core.ads.placement.AdPlacementId
import com.manjul.aikeyboard.core.ads.placement.AdPlacements
import com.manjul.aikeyboard.core.ads.rules.AdSessionTracker
import com.manjul.aikeyboard.core.ads.rules.NavTransition
import com.manjul.aikeyboard.core.common.entry.EntrySource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AdsControllerTest {
    private var now = 1_800_000_000_000L
    private val scope = TestScope(UnconfinedTestDispatcher())
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    private class FakeFullScreenAd : FullScreenAd {
        var shown = 0
        var destroyed = false

        override fun show(activity: Activity, onShown: () -> Unit, onDismissed: () -> Unit) {
            shown++
            onShown()
            onDismissed()
        }

        override fun destroy() {
            destroyed = true
        }
    }

    private class FakeLoader<T> {
        val requests = mutableListOf<String>()
        private var pending: ((T) -> Unit)? = null

        fun load(unitId: String, onLoaded: (T) -> Unit) {
            requests += unitId
            pending = onLoaded
        }

        fun complete(ad: T) {
            pending?.invoke(ad)
            pending = null
        }
    }

    private class FakeNative : NativeAdHandle {
        var destroyed = false

        override fun destroy() {
            destroyed = true
        }
    }

    private val appOpenLoader = FakeLoader<FullScreenAd>()
    private val interstitialLoader = FakeLoader<FullScreenAd>()
    private val nativeLoader = FakeLoader<NativeAdHandle>()
    private val dataStore = MemoryDataStore(AdsState(session = AdSessionState(sessionCount = 1)))
    private val store = DataStoreAdCapStateStore(dataStore)
    private var pro = false

    private val controller = AdsController(
        unitIds = { id: AdPlacementId -> "unit/${id.value}" },
        appOpen = AppOpenAdManager({ unit, ok, _ -> appOpenLoader.load(unit, ok) }) { now },
        interstitial = InterstitialManager({ unit, ok, _ -> interstitialLoader.load(unit, ok) }) { now },
        nativeLoader = { unit, ok, _ -> nativeLoader.load(unit, ok) },
        tracker = AdSessionTracker(store) { now },
        capStore = store,
        isPro = { pro },
        scope = scope,
        clock = { now },
    )

    /** Session 2, ads ready, and every format loaded. */
    private fun ready(): Triple<FakeFullScreenAd, FakeFullScreenAd, FakeNative> {
        controller.onIntent(EntrySource.LAUNCHER)
        controller.onForeground(activity, coldStart = true)
        controller.onAdsReady()
        val appOpen = FakeFullScreenAd().also(appOpenLoader::complete)
        val inter = FakeFullScreenAd().also(interstitialLoader::complete)
        controller.requestNative(AdPlacements.NATIVE_TRANSLATOR)
        val native = FakeNative().also(nativeLoader::complete)
        return Triple(appOpen, inter, native)
    }

    @Test
    fun `an eligible app open shows on resume after a new session`() {
        val (appOpen, _, native) = ready()
        assertEquals(listOf("unit/app_open"), appOpenLoader.requests)
        assertSame(native, controller.nativeAd(AdPlacements.NATIVE_TRANSLATOR).value)
        controller.onBackground()
        now += 31 * 60_000L
        controller.onForeground(activity, coldStart = false)
        assertEquals(1, appOpen.shown)
    }

    @Test
    fun `an interstitial shows on feature to home after two feature uses`() {
        val (_, inter, _) = ready()
        controller.onFeatureUse()
        controller.onNavTransition(activity, NavTransition.FEATURE_TO_HOME)
        assertEquals(0, inter.shown)
        controller.onFeatureUse()
        controller.onNavTransition(activity, NavTransition.OTHER)
        assertEquals(0, inter.shown)
        controller.onNavTransition(activity, NavTransition.FEATURE_TO_HOME)
        assertEquals(1, inter.shown)
        assertEquals(0, dataStore.value.session.featureUsesSinceInterstitial)
    }

    @Test
    fun `pro destroys the loaded app open and native ads and later shows are no-ops`() {
        val (appOpen, inter, native) = ready()

        controller.onProChanged(true)

        assertTrue(appOpen.destroyed)
        assertTrue(inter.destroyed)
        assertTrue(native.destroyed)
        assertNull(controller.nativeAd(AdPlacements.NATIVE_TRANSLATOR).value)
        assertFalse(controller.adsVisible.value)

        controller.onBackground()
        now += 31 * 60_000L
        controller.onForeground(activity, coldStart = false)
        controller.onFeatureUse()
        controller.onFeatureUse()
        controller.onNavTransition(activity, NavTransition.FEATURE_TO_HOME)
        controller.onAdsReady()
        controller.requestNative(AdPlacements.NATIVE_HISTORY)
        assertEquals(0, appOpen.shown)
        assertEquals(0, inter.shown)
        assertEquals(1, appOpenLoader.requests.size)
        assertEquals(1, interstitialLoader.requests.size)
        assertEquals(1, nativeLoader.requests.size)
        assertNull(controller.bannerUnit(AdPlacements.BANNER_HOME))
    }

    @Test
    fun `a stale app open is dropped after 4 h and loaded again`() {
        val (appOpen, _, _) = ready()
        controller.onBackground()
        now += 4 * 3_600_000L
        controller.onForeground(activity, coldStart = false)
        assertEquals(0, appOpen.shown)
        assertEquals(2, appOpenLoader.requests.size)
    }

    @Test
    fun `a rotation is not a foreground`() {
        val (appOpen, _, _) = ready()
        now += 31 * 60_000L
        controller.onForeground(activity, coldStart = false)
        assertEquals(0, appOpen.shown)
    }

    @Test
    fun `a return from an external flow shows no app open`() {
        val (appOpen, _, _) = ready()
        controller.onExternalFlowStart()
        controller.onBackground()
        now += 31 * 60_000L
        controller.onForeground(activity, coldStart = false)
        assertEquals(0, appOpen.shown)
    }

    @Test
    fun `a process text entry stays restricted across a background and resume`() {
        val (appOpen, inter, _) = ready()
        val entries = AdEntryTracker(controller)
        entries.onNewIntent(Intent(Intent.ACTION_PROCESS_TEXT), current = null)
        controller.onBackground()
        entries.onStop()
        now += 31 * 60_000L
        entries.onResume()
        controller.onForeground(activity, coldStart = false)
        controller.onFeatureUse()
        controller.onFeatureUse()
        controller.onNavTransition(activity, NavTransition.FEATURE_TO_HOME)
        assertEquals(EntrySource.PROCESS_TEXT, controller.currentEntry)
        assertEquals(0, appOpen.shown)
        assertEquals(0, inter.shown)

        // Only a real launcher intent ends the restricted entry.
        entries.onNewIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), current = null)
        assertEquals(EntrySource.LAUNCHER, controller.currentEntry)
    }

    @Test
    fun `a recreation keeps the saved entry and ignores the replayed bare intent`() {
        val (appOpen, _, _) = ready()
        val first = AdEntryTracker(controller)
        first.onNewIntent(Intent(Intent.ACTION_SEND), current = null)
        val saved = first.save()

        // The recreated Activity: its intent is the bare ACTION_MAIN, and onCreate replays that same object.
        val bare = Intent(Intent.ACTION_MAIN)
        val second = AdEntryTracker(controller)
        second.onCreate(bare, AdEntryTracker.restore(saved))
        second.onNewIntent(bare, current = bare)
        assertEquals(EntrySource.SEND, controller.currentEntry)
        controller.onBackground()
        now += 31 * 60_000L
        controller.onForeground(activity, coldStart = false)
        assertEquals(0, appOpen.shown)
    }

    @Test
    fun `the IME settings key opens no app-open ad`() {
        val (appOpen, _, _) = ready()
        val entries = AdEntryTracker(controller)
        controller.onBackground()
        entries.onStop()
        now += 31 * 60_000L
        entries.onNewIntent(Intent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), current = null)
        entries.onResume()
        controller.onForeground(activity, coldStart = false)
        assertEquals(0, appOpen.shown)
    }

    @Test
    fun `the paywall or onboarding on screen blocks the app open`() {
        val (appOpen, _, _) = ready()
        controller.setSplashOrOnboarding(true)
        controller.onBackground()
        now += 31 * 60_000L
        controller.onForeground(activity, coldStart = false)
        assertEquals(0, appOpen.shown)
    }
}

private class MemoryDataStore<T>(initial: T) : DataStore<T> {
    private val state = MutableStateFlow(initial)
    override val data: Flow<T> = state
    val value: T get() = state.value

    override suspend fun updateData(transform: suspend (t: T) -> T): T = transform(state.value).also { state.value = it }
}
