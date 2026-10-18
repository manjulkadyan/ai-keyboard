/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.analytics

/** Release sink until a backend is wired. */
object NoopSink : AnalyticsSink {
    override fun log(event: AnalyticsEvent) = Unit
}
