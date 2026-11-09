/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.app.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.manjul.aikeyboard.core.ads.gma.safeAdCallback
import com.manjul.aikeyboard.core.ads.placement.AdFormat
import com.manjul.aikeyboard.core.ads.placement.AdPlacementId
import com.manjul.aikeyboard.core.ads.placement.AdPlacements
import com.manjul.aikeyboard.core.common.NonFatalReporter

/**
 * The GMA loaders behind [AppOpenAdManager], [InterstitialManager] and the native slots. Every GMA call and
 * callback runs in [safeAdCallback] with a fixed area label. Load errors are not logged: their messages can hold
 * data. Each paid callback becomes one `ad_paid_event` through [paid].
 */
internal class GmaAdLoaders(
    context: Context,
    private val reporter: NonFatalReporter,
    private val paid: PaidEventLogger,
) {
    private val app = context.applicationContext

    fun request(): AdRequest = AdRequest.Builder().build()

    fun appOpen(): FullScreenLoader = { unitId, onLoaded, onFailed ->
        val started = safeAdCallback(reporter, "ad_app_open_load") {
            AppOpenAd.load(
                app,
                unitId,
                request(),
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        safeAdCallback(reporter, "ad_app_open_loaded") {
                            ad.setOnPaidEventListener { value -> logPaid(AdPlacements.APP_OPEN, AdFormat.APP_OPEN, value) }
                            onLoaded(
                                GmaFullScreenAd(
                                    reporter,
                                    "ad_app_open",
                                    setCallback = ad::setFullScreenContentCallback,
                                    showAd = ad::show,
                                ),
                            )
                        } ?: onFailed()
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        safeAdCallback(reporter, "ad_app_open_load_failed") { onFailed() }
                    }
                },
            )
        }
        if (started == null) onFailed()
    }

    fun interstitial(): FullScreenLoader = { unitId, onLoaded, onFailed ->
        val started = safeAdCallback(reporter, "ad_interstitial_load") {
            InterstitialAd.load(
                app,
                unitId,
                request(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        safeAdCallback(reporter, "ad_interstitial_loaded") {
                            ad.setOnPaidEventListener { value ->
                                logPaid(AdPlacements.INTER_FEATURE_EXIT, AdFormat.INTERSTITIAL, value)
                            }
                            onLoaded(
                                GmaFullScreenAd(
                                    reporter,
                                    "ad_interstitial",
                                    setCallback = ad::setFullScreenContentCallback,
                                    showAd = ad::show,
                                ),
                            )
                        } ?: onFailed()
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        safeAdCallback(reporter, "ad_interstitial_load_failed") { onFailed() }
                    }
                },
            )
        }
        if (started == null) onFailed()
    }

    /** Native ads for every native placement. The placement id comes from the unit id lookup in [AdsAppGraph]. */
    fun native(placement: (String) -> AdPlacementId?): NativeLoader = { unitId, onLoaded, onFailed ->
        val started = safeAdCallback(reporter, "ad_native_load") {
            val loader = AdLoader.Builder(app, unitId)
                .forNativeAd { ad ->
                    safeAdCallback(reporter, "ad_native_loaded") {
                        placement(unitId)?.let { id ->
                            ad.setOnPaidEventListener { value -> logPaid(id, AdFormat.NATIVE, value) }
                        }
                        onLoaded(GmaNativeAd(ad, reporter))
                    } ?: run {
                        runCatching { ad.destroy() }
                        onFailed()
                    }
                }
                .withAdListener(
                    object : AdListener() {
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            safeAdCallback(reporter, "ad_native_load_failed") { onFailed() }
                        }
                    },
                )
                .build()
            loader.loadAd(request())
        }
        if (started == null) onFailed()
    }

    fun logPaid(id: AdPlacementId, format: AdFormat, value: AdValue) {
        safeAdCallback(reporter, "ad_paid_event") { paid.log(id, format, value) }
    }
}

/** An app-open or interstitial ad. A failure to show counts as a dismiss, so the controller loads the next ad. */
internal class GmaFullScreenAd(
    private val reporter: NonFatalReporter,
    private val area: String,
    private val setCallback: (FullScreenContentCallback?) -> Unit,
    private val showAd: (Activity) -> Unit,
) : FullScreenAd {
    override fun show(activity: Activity, onShown: () -> Unit, onDismissed: () -> Unit) {
        val shown = safeAdCallback(reporter, "${area}_show") {
            setCallback(
                object : FullScreenContentCallback() {
                    override fun onAdShowedFullScreenContent() {
                        safeAdCallback(reporter, "${area}_showed") { onShown() }
                    }

                    override fun onAdDismissedFullScreenContent() {
                        safeAdCallback(reporter, "${area}_dismissed") { onDismissed() }
                    }

                    override fun onAdFailedToShowFullScreenContent(error: AdError) {
                        safeAdCallback(reporter, "${area}_show_failed") { onDismissed() }
                    }
                },
            )
            showAd(activity)
        }
        if (shown == null) onDismissed()
    }

    override fun destroy() {
        safeAdCallback(reporter, "${area}_destroy") { setCallback(null) }
    }
}

/** A loaded GMA native ad. [NativeAdSlot] binds [ad] to a `NativeAdView`. */
internal class GmaNativeAd(val ad: NativeAd, private val reporter: NonFatalReporter) : NativeAdHandle {
    override fun destroy() {
        safeAdCallback(reporter, "ad_native_destroy") { ad.destroy() }
    }
}
