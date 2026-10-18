/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.analytics

interface AnalyticsSink {
    fun log(event: AnalyticsEvent)
}
