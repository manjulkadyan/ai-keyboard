/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.app.ads

import android.app.Activity
import androidx.annotation.MainThread
import com.manjul.aikeyboard.core.ads.cap.AdCapPolicy
import com.manjul.aikeyboard.core.ads.cap.AdCapState
import com.manjul.aikeyboard.core.ads.cap.DataStoreAdCapStateStore
import com.manjul.aikeyboard.core.ads.placement.AdPlacementId
import com.manjul.aikeyboard.core.ads.placement.AdPlacements
import com.manjul.aikeyboard.core.ads.rules.AdContext
import com.manjul.aikeyboard.core.ads.rules.AdEligibility
import com.manjul.aikeyboard.core.ads.rules.AdSessionTracker
import com.manjul.aikeyboard.core.ads.rules.NavTransition
import com.manjul.aikeyboard.core.common.entry.EntrySource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.hours

/** A loaded app-open or interstitial ad. [show] calls [onDismissed] also when the ad fails to show. */
interface FullScreenAd {
    fun show(activity: Activity, onShown: () -> Unit, onDismissed: () -> Unit)

    fun destroy()
}

/** A loaded native ad. The UI reads the GMA ad from [GmaNativeAd]. */
interface NativeAdHandle {
    fun destroy()
}

/** Loads one ad for a unit id and calls exactly one of the two callbacks. */
typealias FullScreenLoader = (unitId: String, onLoaded: (FullScreenAd) -> Unit, onFailed: () -> Unit) -> Unit

typealias NativeLoader = (unitId: String, onLoaded: (NativeAdHandle) -> Unit, onFailed: () -> Unit) -> Unit

/**
 * The app's ad flow (S4 Task 7, TRD §4.11, PRD FR-27). It joins the session tracker, the caps and the pure
 * [AdEligibility] rules, and owns the loaded ads. Every method runs on the main thread. Store writes run in [scope].
 *
 * - App-open: preloaded after `READY`, shown on a return to the app (never on the splash or in onboarding), and
 *   dropped after 4 h.
 * - Interstitial: shown only on [NavTransition.FEATURE_TO_HOME]. No action asks for one.
 * - Banner and native: shown while [adsVisible] and the placement has a unit id.
 * - [onProChanged] with `true` destroys every loaded ad, and later loads and shows do nothing.
 */
class AdsController(
    private val unitIds: (AdPlacementId) -> String?,
    private val appOpen: AppOpenAdManager,
    private val interstitial: InterstitialManager,
    private val nativeLoader: NativeLoader,
    private val tracker: AdSessionTracker,
    private val capStore: DataStoreAdCapStateStore,
    private val isPro: () -> Boolean,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val capPolicy: AdCapPolicy = AdCapPolicy(),
) {
    private var proFlag = false
    private var adsReady = false
    private var entry = EntrySource.LAUNCHER
    private var splashOrOnboarding = false
    private var externalFlow = false
    private var backgrounded = false
    private var capState = AdCapState()

    private val natives = mutableMapOf<AdPlacementId, MutableStateFlow<NativeAdHandle?>>()
    private val nativeLoadedAt = mutableMapOf<AdPlacementId, Long>()
    private val nativeLoading = mutableSetOf<AdPlacementId>()

    private val _adsVisible = MutableStateFlow(false)

    /** True when banner and native slots can show: not Pro, the SDK is ready and this is not the first session. */
    val adsVisible: StateFlow<Boolean> = _adsVisible.asStateFlow()

    init {
        scope.launch { capState = capStore.read() }
    }

    private fun pro(): Boolean = proFlag || isPro()

    private fun unit(id: AdPlacementId): String? = if (pro()) null else unitIds(id)

    private fun context(id: AdPlacementId, now: Long, external: Boolean = externalFlow) = AdContext(
        isPro = pro(),
        adsReady = adsReady,
        sessionIndex = tracker.sessionIndex,
        entry = entry,
        onSplashOrOnboarding = splashOrOnboarding,
        returningFromExternalFlow = external,
        featureUsesSinceInterstitial = tracker.featureUsesSinceInterstitial,
        lastInterstitialAtMs = tracker.lastInterstitialAtMs,
        nowMs = now,
        cap = capPolicy.evaluate(AdPlacements.registry.require(id), capState, now),
    )

    private fun refreshVisible() {
        _adsVisible.value = !pro() && adsReady && tracker.sessionIndex > 1
    }

    /** The entry source in effect for the ad rules. */
    internal val currentEntry: EntrySource get() = entry

    /**
     * The entry source of a new intent, or [EntrySource.RESUME] for a return without one. A restricted entry
     * ([EntrySource.PROCESS_TEXT], [EntrySource.SEND], [EntrySource.NOTIFICATION]) is sticky. Only a launcher intent
     * or another restricted entry replaces it. A resume or a deep link does not.
     */
    @MainThread
    fun onIntent(source: EntrySource) {
        entry = when {
            source == EntrySource.LAUNCHER -> source
            entry in stickyEntries && source !in stickyEntries -> entry
            else -> source
        }
    }

    /** True while the splash or onboarding is on screen: no app-open ad then. */
    @MainThread
    fun setSplashOrOnboarding(active: Boolean) {
        splashOrOnboarding = active
    }

    /** The Activity starts another app or screen (share sheet, Billing, browser, update flow). */
    @MainThread
    fun onExternalFlowStart() {
        externalFlow = true
    }

    /** The GMA SDK is `READY`: preload the app-open and interstitial ads. */
    @MainThread
    fun onAdsReady() {
        adsReady = true
        refreshVisible()
        preloadFullScreen()
    }

    /**
     * The Activity is in the foreground. A cold start, or a start after [onBackground], counts. Any other call
     * (a configuration change) does nothing.
     */
    @MainThread
    fun onForeground(activity: Activity, coldStart: Boolean) {
        if (!coldStart && !backgrounded) {
            // A resume without a background (rotation, a dialog-like flow): a started flow did not leave the app.
            externalFlow = false
            return
        }
        backgrounded = false
        val external = externalFlow
        externalFlow = false
        scope.launch {
            tracker.onAppStart(coldStart)
            refreshVisible()
            if (pro()) return@launch
            val now = clock()
            if (!appOpen.isAvailable()) {
                preloadFullScreen()
                return@launch
            }
            val unitId = unit(AdPlacements.APP_OPEN)
            if (AdEligibility.appOpen(context(AdPlacements.APP_OPEN, now, external), unitId) && activity.canShow()) {
                appOpen.show(
                    activity,
                    onShown = {
                        recordShown(AdPlacements.APP_OPEN)
                        scope.launch { tracker.onAppOpenShown() }
                    },
                    onDismissed = ::preloadFullScreen,
                )
            }
        }
    }

    @MainThread
    fun onBackground() {
        backgrounded = true
        scope.launch { tracker.onBackground() }
    }

    /** A completed feature use: `translate_done` on the Translator, a copy or reuse in History, or a theme apply. */
    @MainThread
    fun onFeatureUse() {
        scope.launch { tracker.onFeatureUse() }
    }

    /** The only way to an interstitial: [NavTransition.FEATURE_TO_HOME] after enough feature uses. */
    @MainThread
    fun onNavTransition(activity: Activity, transition: NavTransition) {
        if (pro()) return
        val unitId = unit(AdPlacements.INTER_FEATURE_EXIT)
        val ctx = context(AdPlacements.INTER_FEATURE_EXIT, clock())
        if (!AdEligibility.interstitial(ctx, transition, unitId) || !activity.canShow()) return
        if (!interstitial.isAvailable()) {
            preloadFullScreen()
            return
        }
        interstitial.show(
            activity,
            onShown = {
                recordShown(AdPlacements.INTER_FEATURE_EXIT)
                scope.launch { tracker.onInterstitialShown() }
            },
            onDismissed = ::preloadFullScreen,
        )
    }

    @MainThread
    fun onProChanged(isPro: Boolean) {
        proFlag = isPro
        if (isPro) {
            appOpen.destroy()
            interstitial.destroy()
            natives.forEach { (id, flow) ->
                flow.value?.destroy()
                flow.value = null
                nativeLoadedAt.remove(id)
            }
            nativeLoading.clear()
        }
        refreshVisible()
        if (!isPro) preloadFullScreen()
    }

    /** The banner unit id for [id], or null when the slot must stay empty. */
    @MainThread
    fun bannerUnit(id: AdPlacementId): String? {
        val unitId = unit(id)
        return unitId.takeIf { AdEligibility.bannerOrNative(context(id, clock()), it) }
    }

    @MainThread
    fun nativeAd(id: AdPlacementId): StateFlow<NativeAdHandle?> = nativeFlow(id).asStateFlow()

    /** Loads the native ad of [id] once. A loaded ad is reused for [NATIVE_MAX_AGE_MS], then loaded again. */
    @MainThread
    fun requestNative(id: AdPlacementId) {
        val unitId = unit(id)
        if (!AdEligibility.bannerOrNative(context(id, clock()), unitId) || unitId == null) return
        val flow = nativeFlow(id)
        val loadedAt = nativeLoadedAt[id]
        if (flow.value != null && loadedAt != null && clock() - loadedAt < NATIVE_MAX_AGE_MS) return
        if (!nativeLoading.add(id)) return
        nativeLoader(
            unitId,
            { ad ->
                nativeLoading.remove(id)
                if (pro()) {
                    ad.destroy()
                } else {
                    flow.value?.destroy()
                    flow.value = ad
                    nativeLoadedAt[id] = clock()
                }
            },
            { nativeLoading.remove(id) },
        )
    }

    private fun nativeFlow(id: AdPlacementId) = natives.getOrPut(id) { MutableStateFlow(null) }

    private fun preloadFullScreen() {
        if (pro() || !adsReady || tracker.sessionIndex <= 1) return
        unit(AdPlacements.APP_OPEN)?.let { unitId -> appOpen.load(unitId) { pro() } }
        unit(AdPlacements.INTER_FEATURE_EXIT)?.let { unitId -> interstitial.load(unitId) { pro() } }
    }

    private fun recordShown(id: AdPlacementId) {
        val placement = AdPlacements.registry.require(id)
        val now = clock()
        capState = capPolicy.recordShown(placement, capState, now)
        scope.launch { capState = capStore.update { capPolicy.recordShown(placement, it, now) } }
    }

    private fun Activity.canShow(): Boolean = !isFinishing && !isDestroyed

    companion object {
        /** GMA native ads expire after an hour. */
        val NATIVE_MAX_AGE_MS: Long = 1.hours.inWholeMilliseconds

        private val stickyEntries = setOf(EntrySource.PROCESS_TEXT, EntrySource.SEND, EntrySource.NOTIFICATION)
    }
}
