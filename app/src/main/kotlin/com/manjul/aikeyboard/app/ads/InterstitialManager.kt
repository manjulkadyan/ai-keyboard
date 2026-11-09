/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.app.ads

import kotlin.time.Duration.Companion.hours

/**
 * The preloaded `inter_feature_exit` interstitial. Only [AdsController.onNavTransition] shows it: Translate, Mic,
 * Copy, Apply theme and Purchase never call this class. A loaded ad expires after [MAX_AGE_MS].
 */
class InterstitialManager(
    loader: FullScreenLoader,
    clock: () -> Long = System::currentTimeMillis,
) : FullScreenAdSlot(loader, clock, MAX_AGE_MS) {
    companion object {
        val MAX_AGE_MS: Long = 1.hours.inWholeMilliseconds
    }
}
