/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.analytics

interface AnalyticsSink {
    fun log(event: AnalyticsEvent)

    /**
     * Sets a user property (for example `is_pro`). The value is an enum or a bucket, never user content; null clears
     * it. Sinks without user properties ignore it.
     */
    fun setUserProperty(name: String, value: String?) = Unit
}
