/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.analytics

import android.util.Log

/** Debug-only sink. The catalog guarantees params carry no user content. */
object LogcatSink : AnalyticsSink {
    const val TAG = "AiKbAnalytics"

    override fun log(event: AnalyticsEvent) {
        Log.d(TAG, "${event.name} ${event.params}")
    }
}
