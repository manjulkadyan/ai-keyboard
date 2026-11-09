/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ads.rules

import com.manjul.aikeyboard.core.ads.cap.AdSessionState
import com.manjul.aikeyboard.core.ads.cap.DataStoreAdCapStateStore
import kotlin.time.Duration.Companion.minutes

/**
 * Ad sessions and feature uses (plan decisions 14 and 15). A session starts at a cold start of the main Activity,
 * or on a return after at least [NEW_SESSION_AFTER_MS] in the background. A new session and an interstitial both
 * reset the feature-use count. The state is persisted in `ads_state.json`; the getters read the last known value.
 */
class AdSessionTracker(
    private val store: DataStoreAdCapStateStore,
    private val clock: () -> Long,
) {
    @Volatile
    private var state = AdSessionState()

    /** 1 for the first session after install. 0 before the first [onAppStart]. */
    val sessionIndex: Int get() = state.sessionCount

    val featureUsesSinceInterstitial: Int get() = state.featureUsesSinceInterstitial

    val lastInterstitialAtMs: Long get() = state.lastInterstitialAtMs

    /** Loads the persisted counters without starting a session. */
    suspend fun load() {
        state = store.readSession()
    }

    /** Returns true when this start began a new session. */
    suspend fun onAppStart(coldStart: Boolean): Boolean {
        val now = clock()
        var started = false
        state = store.updateSession { s ->
            val background = s.lastBackgroundAtMs
            started = coldStart || (background > 0 && now - background >= NEW_SESSION_AFTER_MS)
            if (started) {
                s.copy(sessionCount = s.sessionCount + 1, featureUsesSinceInterstitial = 0, lastBackgroundAtMs = 0)
            } else {
                s.copy(lastBackgroundAtMs = 0)
            }
        }
        return started
    }

    suspend fun onBackground() {
        val now = clock()
        state = store.updateSession { it.copy(lastBackgroundAtMs = now) }
    }

    /** A completed use: `translate_done` on the Translator, a copy or reuse in History, or a theme apply. */
    suspend fun onFeatureUse() {
        state = store.updateSession { it.copy(featureUsesSinceInterstitial = it.featureUsesSinceInterstitial + 1) }
    }

    suspend fun onInterstitialShown() {
        val now = clock()
        state = store.updateSession { it.copy(featureUsesSinceInterstitial = 0, lastInterstitialAtMs = now) }
    }

    suspend fun onAppOpenShown() {
        val now = clock()
        state = store.updateSession { it.copy(lastAppOpenAtMs = now) }
    }

    companion object {
        val NEW_SESSION_AFTER_MS: Long = 30.minutes.inWholeMilliseconds
    }
}
