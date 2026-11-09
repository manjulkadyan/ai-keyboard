/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.settings

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/**
 * The "Ad privacy options" row of Settings → Privacy (UMP privacy options entry point). The ads stack implements
 * it in `app.ads` (`AdsAppGraph.privacyOptions`) and `Routes.kt` passes it in, so this package never references
 * `:core:ads` (`ImeAdIsolationTest`).
 */
interface AdPrivacyOptions {
    /** True when UMP says the user must be able to change their ad consent. */
    val required: StateFlow<Boolean>

    /** Shows the UMP privacy options form. Call on the main thread. */
    fun show(activity: Activity)
}
