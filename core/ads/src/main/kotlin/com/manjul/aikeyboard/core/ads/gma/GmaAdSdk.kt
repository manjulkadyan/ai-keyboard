/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ads.gma

import android.app.Activity
import com.manjul.aikeyboard.core.ads.AdSdk
import com.manjul.aikeyboard.core.common.NonFatalReporter
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Google Mobile Ads behind [com.manjul.aikeyboard.core.ads.AdsInitializer]'s consent gate (S4 Task 6). It starts the
 * SDK on [io] and posts the result to the main thread. Every SDK call and callback goes through [safeAdCallback]:
 * a failure is reported to [reporter] once and ends as `onFailure` (SDK_FAILED), never as a crash.
 */
class GmaAdSdk(
    private val facade: MobileAdsFacade,
    private val io: CoroutineDispatcher,
    private val reporter: NonFatalReporter,
) : AdSdk {
    private val scope = CoroutineScope(SupervisorJob() + io)

    override fun initialize(activity: Activity, onComplete: () -> Unit, onFailure: () -> Unit) {
        // The application context: the SDK outlives this Activity. The stub Activity in JVM tests has none.
        val context = activity.applicationContext ?: activity
        val finished = AtomicBoolean(false)
        fun finish(callback: () -> Unit, area: String) {
            if (!finished.compareAndSet(false, true)) return
            CoroutineScope(Dispatchers.Main).launch { safeAdCallback(reporter, area, callback) }
        }
        scope.launch {
            val started = safeAdCallback(reporter, AREA_INIT) {
                facade.initialize(context) { finish(onComplete, AREA_COMPLETE) }
            }
            if (started == null) finish(onFailure, AREA_FAILURE)
        }
    }

    private companion object {
        const val AREA_INIT = "ads_sdk_init"
        const val AREA_COMPLETE = "ads_sdk_init_complete"
        const val AREA_FAILURE = "ads_sdk_init_failure"
    }
}
