/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.app.ads

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.manjul.aikeyboard.core.ads.gma.safeAdCallback
import com.manjul.aikeyboard.core.ads.placement.AdFormat
import com.manjul.aikeyboard.core.ads.placement.AdPlacements

/** The `banner_home` slot: an anchored adaptive banner at the bottom of Home. Empty for Pro or without a unit id. */
@Composable
fun HomeBannerSlot() {
    val graph = LocalAdsAppGraph.current ?: return
    val visible by graph.controller.adsVisible.collectAsState()
    if (!visible) return
    val unitId = graph.controller.bannerUnit(AdPlacements.BANNER_HOME) ?: return
    val widthDp = LocalConfiguration.current.screenWidthDp
    // A new unit id or width needs a new AdView: GMA sets the size and the unit once per view.
    key(unitId, widthDp) { BannerView(graph, unitId, widthDp) }
}

@Composable
private fun BannerView(graph: AdsAppGraph, unitId: String, widthDp: Int) {
    val holder = remember { arrayOfNulls<AdView>(1) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        holder[0]?.let { view -> safeAdCallback(graph.reporter, "ad_banner_pause") { view.pause() } }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        holder[0]?.let { view -> safeAdCallback(graph.reporter, "ad_banner_resume") { view.resume() } }
    }
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).also { view ->
                holder[0] = view
                safeAdCallback(graph.reporter, "ad_banner_load") {
                    view.setAdSize(AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, widthDp))
                    view.adUnitId = unitId
                    view.setOnPaidEventListener { value ->
                        graph.loaders.logPaid(AdPlacements.BANNER_HOME, AdFormat.BANNER, value)
                    }
                    view.loadAd(graph.loaders.request())
                }
            }
        },
        onRelease = { view ->
            holder[0] = null
            safeAdCallback(graph.reporter, "ad_banner_destroy") { view.destroy() }
        },
    )
}
