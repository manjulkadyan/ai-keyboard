/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.app.ads

import kotlin.time.Duration.Companion.hours

/** The preloaded app-open ad. A loaded ad expires after [MAX_AGE_MS] (4 h, the GMA limit for app-open ads). */
class AppOpenAdManager(
    loader: FullScreenLoader,
    clock: () -> Long = System::currentTimeMillis,
) : FullScreenAdSlot(loader, clock, MAX_AGE_MS) {
    companion object {
        val MAX_AGE_MS: Long = 4.hours.inWholeMilliseconds
    }
}
