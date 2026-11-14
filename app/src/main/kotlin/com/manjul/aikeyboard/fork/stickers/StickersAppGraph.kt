/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.stickers

import android.content.Context
import android.net.ConnectivityManager
import com.manjul.aikeyboard.appContext
import com.manjul.aikeyboard.appDatabase
import com.manjul.aikeyboard.contentManifest
import com.manjul.aikeyboard.featureSettingsStore
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.AnalyticsSink
import com.manjul.aikeyboard.core.content.stickers.StickerRepository
import com.manjul.aikeyboard.packDownloader

/**
 * The sticker objects (S5 Task 6, PRD FR-23), built on first use. The manifest and the pack downloads use the
 * shared content source, which sends no user identifier (FR-23 AC4). No ads here (decision 19).
 */
class StickersAppGraph(context: Context) {
    private val app = context.applicationContext

    val analytics: AnalyticsSink = RelaySink(app)

    val repository: StickerRepository by lazy {
        StickerRepository(
            manifest = app.contentManifest().value,
            downloader = app.packDownloader().value,
            dao = app.appDatabase().value.stickerDao(),
            bundled = BundledStarterPack(app),
            analytics = analytics,
            canDownload = ::canDownload,
        )
    }

    /**
     * False while "Download on Wi-Fi only" (`contentWifiOnly`) is on and the active network is metered. The panel
     * then shows "Waits for Wi-Fi" and nothing is fetched (S5 Task 6 review 1).
     */
    private fun canDownload(): Boolean {
        if (!app.featureSettingsStore().value.current.contentWifiOnly) return true
        val connectivity = app.getSystemService(ConnectivityManager::class.java) ?: return false
        return !connectivity.isActiveNetworkMetered
    }

    val shareCache: ShareCache by lazy { ShareCache(app.cacheDir, System::currentTimeMillis) }

    private class RelaySink(private val app: Context) : AnalyticsSink {
        override fun log(event: AnalyticsEvent) = app.appContext().value.analytics.value.log(event)
    }
}
