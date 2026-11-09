/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ads.gma

import android.content.Context
import com.google.android.gms.ads.MobileAds

/** The one GMA call [GmaAdSdk] makes, behind an interface so tests run without the SDK. */
interface MobileAdsFacade {
    /** Starts the SDK. [onDone] runs when the adapters finished starting (on a thread the SDK picks). */
    fun initialize(context: Context, onDone: () -> Unit)
}

/**
 * The real `MobileAds.initialize`. The manifest sets `OPTIMIZE_INITIALIZATION`, so Google supports this call off the
 * main thread. Nothing references the GMA classes until [initialize] runs.
 */
object GoogleMobileAdsFacade : MobileAdsFacade {
    override fun initialize(context: Context, onDone: () -> Unit) {
        MobileAds.initialize(context) { onDone() }
    }
}
