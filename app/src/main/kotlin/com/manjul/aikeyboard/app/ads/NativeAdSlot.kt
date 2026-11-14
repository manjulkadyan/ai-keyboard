/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.app.ads

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.app.LocalNavController
import com.manjul.aikeyboard.app.Routes
import com.manjul.aikeyboard.core.ads.gma.safeAdCallback
import com.manjul.aikeyboard.core.ads.placement.AdPlacements
import com.manjul.aikeyboard.fork.paywall.PaywallTrigger
import org.florisboard.lib.compose.stringRes

/** The Translator's native slot, the last item of its screen. */
@Composable
fun TranslatorNativeAdSlot() = NativeAdSlot(Placement.TRANSLATOR)

/** The History native slot, the last item of its list. */
@Composable
fun HistoryNativeAdSlot() = NativeAdSlot(Placement.HISTORY)

/** The Dictionary screen's native slot, below the attribution footer (S5-dict). */
@Composable
fun DictionaryNativeAdSlot() = NativeAdSlot(Placement.DICTIONARY)

internal enum class Placement { TRANSLATOR, HISTORY, DICTIONARY }

/**
 * A native ad below the fold, as the last item of a screen. The ad view carries an "Ad" label. The "Remove ads"
 * link sits outside the ad container and opens `paywall?trigger=remove_ads`. Nothing shows for Pro, before ads
 * are ready, in the first session, or without a unit id.
 */
@Composable
internal fun NativeAdSlot(placement: Placement) {
    val id = when (placement) {
        Placement.TRANSLATOR -> AdPlacements.NATIVE_TRANSLATOR
        Placement.HISTORY -> AdPlacements.NATIVE_HISTORY
        Placement.DICTIONARY -> AdPlacements.NATIVE_DICTIONARY
    }
    val graph = LocalAdsAppGraph.current ?: return
    val visible by graph.controller.adsVisible.collectAsState()
    val handle by graph.controller.nativeAd(id).collectAsState()
    LaunchedEffect(visible, id) {
        if (visible) graph.controller.requestNative(id)
    }
    val ad = (handle as? GmaNativeAd)?.ad
    if (!visible || ad == null) return
    val navController = LocalNavController.current
    val onSurface = MaterialTheme.colorScheme.onSurface.toArgb()
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    val adLabel = stringRes(R.string.ads__native_label)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context -> NativeAdBinder.create(context, adLabel, onSurface, accent) },
            update = { view -> safeAdCallback(graph.reporter, "ad_native_bind") { NativeAdBinder.bind(view, ad) } },
            onRelease = { view -> safeAdCallback(graph.reporter, "ad_native_view_destroy") { view.destroy() } },
        )
        TextButton(
            modifier = Modifier.align(Alignment.End),
            onClick = { navController.navigate(Routes.Paywall(PaywallTrigger.REMOVE_ADS.wire)) },
        ) {
            Text(stringRes(R.string.ads__remove_ads_link))
        }
    }
}

/** Builds the `NativeAdView` in code (no layout XML) and binds a [NativeAd] to it. */
private object NativeAdBinder {
    fun create(context: Context, adLabel: String, textColor: Int, accent: Int): NativeAdView {
        fun dp(value: Int): Int =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics)
                .toInt()

        val label = TextView(context).apply {
            text = adLabel
            setTextColor(accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        }
        val icon = ImageView(context).apply { layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)) }
        val headline = TextView(context).apply {
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        }
        val advertiser = TextView(context).apply {
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        val titles = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, 0, 0)
            addView(headline)
            addView(advertiser)
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(icon)
            addView(titles)
        }
        val body = TextView(context).apply {
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        val media = MediaView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(180))
        }
        val cta = Button(context)
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            addView(label)
            addView(header)
            addView(body)
            addView(media)
            addView(cta)
        }
        return NativeAdView(context).apply {
            addView(column)
            headlineView = headline
            bodyView = body
            iconView = icon
            advertiserView = advertiser
            callToActionView = cta
            mediaView = media
        }
    }

    fun bind(view: NativeAdView, ad: NativeAd) {
        (view.headlineView as TextView).text = ad.headline
        (view.bodyView as TextView).setTextOrHide(ad.body)
        (view.advertiserView as TextView).setTextOrHide(ad.advertiser)
        (view.callToActionView as Button).setTextOrHide(ad.callToAction)
        val icon = view.iconView as ImageView
        val drawable = ad.icon?.drawable
        icon.setImageDrawable(drawable)
        icon.visibility = if (drawable == null) View.GONE else View.VISIBLE
        ad.mediaContent?.let { view.mediaView?.mediaContent = it }
        view.setNativeAd(ad)
    }

    private fun TextView.setTextOrHide(value: String?) {
        text = value.orEmpty()
        visibility = if (value.isNullOrEmpty()) View.GONE else View.VISIBLE
    }
}
