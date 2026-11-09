/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.app.ads

import android.app.Activity

/**
 * One preloaded full-screen ad: at most one load at a time, and a loaded ad older than [maxAgeMs] is dropped.
 * A shown ad is used up. Main thread only.
 */
abstract class FullScreenAdSlot internal constructor(
    private val loader: FullScreenLoader,
    private val clock: () -> Long,
    private val maxAgeMs: Long,
) {
    private var ad: FullScreenAd? = null
    private var loadedAt = 0L
    private var loading = false

    /** True when a fresh ad is loaded. A stale ad is destroyed here. */
    fun isAvailable(): Boolean {
        val current = ad ?: return false
        if (clock() - loadedAt < maxAgeMs) return true
        current.destroy()
        ad = null
        return false
    }

    /** Starts a load unless one is running or a fresh ad is loaded. [discard] is read again when the ad arrives. */
    fun load(unitId: String, discard: () -> Boolean = { false }) {
        if (loading || isAvailable()) return
        loading = true
        loader(
            unitId,
            { loaded ->
                loading = false
                if (discard()) {
                    loaded.destroy()
                } else {
                    ad?.destroy()
                    ad = loaded
                    loadedAt = clock()
                }
            },
            { loading = false },
        )
    }

    /** Shows the loaded ad once. Returns false when no fresh ad is loaded. */
    fun show(activity: Activity, onShown: () -> Unit, onDismissed: () -> Unit): Boolean {
        if (!isAvailable()) return false
        val current = ad ?: return false
        ad = null
        current.show(activity, onShown, onDismissed)
        return true
    }

    fun destroy() {
        ad?.destroy()
        ad = null
    }
}
