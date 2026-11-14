/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.app.ads

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.annotation.MainThread
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.manjul.aikeyboard.app.FlorisPreferenceStore
import com.manjul.aikeyboard.appContext
import com.manjul.aikeyboard.core.ads.AdsInitializer
import com.manjul.aikeyboard.core.ads.AdsStatus
import com.manjul.aikeyboard.core.ads.cap.AdCapState
import com.manjul.aikeyboard.core.ads.cap.AdSessionState
import com.manjul.aikeyboard.core.ads.cap.DataStoreAdCapStateStore
import com.manjul.aikeyboard.core.ads.consent.ConsentModeSignals
import com.manjul.aikeyboard.core.ads.consent.ConsentResult
import com.manjul.aikeyboard.core.ads.consent.UmpConsentManager
import com.manjul.aikeyboard.core.ads.gma.GmaAdSdk
import com.manjul.aikeyboard.core.ads.gma.GoogleMobileAdsFacade
import com.manjul.aikeyboard.core.ads.placement.AdPlacementId
import com.manjul.aikeyboard.core.ads.placement.AdPlacements
import com.manjul.aikeyboard.core.ads.rules.AdSessionTracker
import com.manjul.aikeyboard.core.ads.rules.AdUnitIds
import com.manjul.aikeyboard.core.common.NonFatalReporter
import com.manjul.aikeyboard.core.common.entry.EntrySource
import com.manjul.aikeyboard.core.config.ConfigKeys
import com.manjul.aikeyboard.core.datastore.FeatureSettings
import com.manjul.aikeyboard.featureSettingsStore
import com.manjul.aikeyboard.firebaseGraph
import com.manjul.aikeyboard.fork.billing.proGate
import com.manjul.aikeyboard.fork.settings.AdPrivacyOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The app-process ads stack (S4 Task 6): UMP consent, then the GMA SDK. One per process, created on the first
 * [get] from the main Activity. It is not a field on `FlorisApplication`, which the IME loads: only Activity code
 * reaches this package (`ImeAdIsolationTest`), so an IME-only process never loads it or any GMA class.
 */
class AdsAppGraph(private val app: Application) {
    private val settings by app.featureSettingsStore()
    private val firebase by app.firebaseGraph()
    private val isPro by lazy { proGate(settings) }

    private val consentManager = UmpConsentManager()

    val initializer: AdsInitializer = AdsInitializer(
        consentManager = consentManager,
        adSdk = GmaAdSdk(GoogleMobileAdsFacade, Dispatchers.IO, firebase.reporter),
        // Read again when consent resolves: a purchase or a kill switch during the form keeps the SDK off.
        adsDisabled = { !adsAllowed() },
        onConsent = ::onConsent,
    )
    val status: StateFlow<AdsStatus> = initializer.status

    // S4 Task 7: placements, rules and the loaded ads. Nothing here loads an ad before the SDK is READY.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val unitIds by lazy { AdUnitIds(firebase.remoteConfig) }
    private val nativePlacements =
        listOf(AdPlacements.NATIVE_TRANSLATOR, AdPlacements.NATIVE_HISTORY, AdPlacements.NATIVE_DICTIONARY) // S5-dict

    internal val reporter: NonFatalReporter get() = firebase.reporter

    /** S4 Task 11: the "Ad privacy options" row of Settings → Privacy, wired in `Routes.kt`. */
    val privacyOptions: AdPrivacyOptions by lazy {
        val required = status.map { it.privacyOptionsRequired }
            .stateIn(scope, SharingStarted.Eagerly, status.value.privacyOptionsRequired)
        object : AdPrivacyOptions {
            override val required: StateFlow<Boolean> = required

            override fun show(activity: Activity, onResult: (Boolean) -> Unit) {
                consentManager.showPrivacyOptionsForm(activity) { error ->
                    onResult(privacyOptionsResult(error, reporter))
                }
            }
        }
    }

    internal val loaders: GmaAdLoaders by lazy {
        GmaAdLoaders(app, firebase.reporter, PaidEventLogger { app.appContext().value.analytics.value.log(it) })
    }

    /** One store per process: two DataStores on `ads_state.json` in one process would fail. */
    private val capStore by lazy { DataStoreAdCapStateStore.create(app) }

    val controller: AdsController by lazy {
        val store = capStore
        AdsController(
            unitIds = { id: AdPlacementId -> unitIds.unitFor(id) },
            appOpen = AppOpenAdManager(loaders.appOpen()),
            interstitial = InterstitialManager(loaders.interstitial()),
            nativeLoader = loaders.native { unit -> nativePlacements.firstOrNull { unitIds.unitFor(it) == unit } },
            tracker = AdSessionTracker(store, System::currentTimeMillis),
            capStore = store,
            // Pro or the ads_enabled_global kill switch: no load and no show.
            isPro = { !adsAllowed() },
            scope = scope,
        )
    }

    /**
     * Delete my data (S4 Task 12): resets the persisted ad caps and session counters in `ads_state.json`. A running
     * [AdSessionTracker] keeps its in-memory copy until the next process start.
     */
    suspend fun clearAdState() {
        capStore.update { AdCapState() }
        capStore.updateSession { AdSessionState() }
    }

    private var observing = false
    private var attachedInProcess = false

    /**
     * Called by the main Activity on each cold start. Starts consent and the SDK only when onboarding is done, the
     * user is not Pro and the `ads_enabled_global` kill switch is on. Otherwise nothing of UMP or GMA runs.
     */
    @MainThread
    fun onAppStart(activity: Activity, onboardingDone: Boolean) {
        if (!onboardingDone || !adsAllowed()) return
        initializer.initialize(activity)
    }

    /**
     * Connects the main Activity to [controller] (S4 Task 7). It classifies each intent into an [EntrySource] and
     * reports the foreground and the background. A configuration change is neither a background nor a foreground.
     * The Activity reports the screens it starts through [onExternalFlowStart].
     */
    @MainThread
    fun attach(activity: ComponentActivity) {
        val coldStart = !attachedInProcess
        attachedInProcess = true
        observeOnce()
        val entries = AdEntryTracker(controller)
        val registry = activity.savedStateRegistry
        entries.onCreate(activity.intent, AdEntryTracker.restore(registry.consumeRestoredStateForKey(SAVED_STATE_KEY)))
        registry.registerSavedStateProvider(SAVED_STATE_KEY, entries::save)
        controller.setSplashOrOnboarding(true)
        var firstResume = true
        activity.addOnNewIntentListener(Consumer<Intent> { intent -> entries.onNewIntent(intent, activity.intent) })
        activity.lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        entries.onResume()
                        controller.onForeground(activity, coldStart = coldStart && firstResume)
                        firstResume = false
                    }
                    Lifecycle.Event.ON_STOP -> if (!activity.isChangingConfigurations) {
                        entries.onStop()
                        controller.onBackground()
                    }
                    else -> Unit
                }
            },
        )
        observeThemeApply(activity)
    }

    /**
     * The main Activity starts another screen (share sheet, Billing, browser, update flow, a system screen). The
     * return from it is not an app open.
     */
    @MainThread
    fun onExternalFlowStart() {
        controller.onExternalFlowStart()
    }

    /** The entry source in effect (S4 Task 8 reads it for the update prompt). Read only. */
    val currentEntry: EntrySource get() = controller.currentEntry

    /** The SDK state and the Pro state, observed once per process. */
    private fun observeOnce() {
        if (observing) return
        observing = true
        scope.launch {
            status.collect { if (it.phase == AdsStatus.Phase.READY) controller.onAdsReady() }
        }
        scope.launch {
            settings.flow.collect { controller.onProChanged(!adsAllowed()) }
        }
    }

    /** A theme apply (day or night theme changed while the Activity runs) is a feature use. */
    private fun observeThemeApply(activity: ComponentActivity) {
        activity.lifecycleScope.launch {
            app.appContext().value.preferenceStoreLoaded.first { it }
            val prefs by FlorisPreferenceStore
            merge(prefs.theme.dayThemeId.asFlow().drop(1), prefs.theme.nightThemeId.asFlow().drop(1))
                .collect { controller.onFeatureUse() }
        }
    }

    private fun adsAllowed(): Boolean = !isPro() && firebase.remoteConfig.bool(ConfigKeys.ADS_ENABLED_GLOBAL)

    /**
     * Consent Mode v2 for Analytics from the UMP result and the TCF strings UMP stored (TRD §4.11), and the stored
     * Analytics consent that gates every Firebase Analytics event (I-1, TRD §4.14 "Consent"): set when
     * `analytics_storage` is granted, cleared when it is denied.
     */
    private fun onConsent(result: ConsentResult) {
        val signals = ConsentModeSignals.read(app, result.canRequestAds)
        firebase.setConsentMode(
            adStorage = signals.adStorage,
            adUserData = signals.adUserData,
            adPersonalization = signals.adPersonalization,
            analyticsStorage = signals.analyticsStorage,
        )
        scope.launch {
            settings.update { analyticsConsentFrom(it, signals.analyticsStorage, System.currentTimeMillis()) }
            firebase.syncAnalyticsCollection()
        }
    }

    companion object {
        private const val SAVED_STATE_KEY = AdEntryTracker.SAVED_STATE_KEY

        /** [old] with the Analytics consent of one UMP result: the first grant time is kept, a denial clears it. */
        internal fun analyticsConsentFrom(old: FeatureSettings, analyticsStorage: Boolean, now: Long): FeatureSettings =
            when {
                !analyticsStorage -> old.copy(analyticsConsentAt = 0L)
                old.analyticsConsentAt > 0L -> old
                else -> old.copy(analyticsConsentAt = now)
            }

        @Volatile
        private var instance: AdsAppGraph? = null

        fun get(app: Application): AdsAppGraph =
            instance ?: synchronized(this) { instance ?: AdsAppGraph(app).also { instance = it } }

        /**
         * A completed feature use from a screen of the main Activity (for example `translate_done`). It does
         * nothing until the main Activity created the graph.
         */
        @MainThread
        fun onFeatureUse() {
            instance?.controller?.onFeatureUse()
        }
    }
}

/**
 * The ads graph for the composables of this package. Only the main Activity provides it, so a screen composed
 * anywhere else (a test, another Activity) shows no ad slot and reports no navigation.
 */
val LocalAdsAppGraph = staticCompositionLocalOf<AdsAppGraph?> { null }
