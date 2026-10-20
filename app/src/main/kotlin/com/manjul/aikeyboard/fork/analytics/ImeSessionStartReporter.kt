/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.analytics

import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.analytics.SessionSampler
import com.manjul.aikeyboard.ime.policy.PolicyDecision

/**
 * Sends `ime_session_start` (PRD §12, R36) when the keyboard is shown in a field, sampled 1 per 10 min. A
 * sensitive field reports `is_sensitive=true`, the one event the minimal analytics policy lets through.
 * One per IME service instance; main thread.
 */
class ImeSessionStartReporter(clock: () -> Long, private val log: (AnalyticsEvent) -> Unit) {
    private val sampler = SessionSampler(clock)

    /** [subtype] is the active subtype's primary language tag. */
    fun onStartInputView(decision: PolicyDecision, subtype: String) {
        if (sampler.shouldLogImeSession()) log(EventCatalog.imeSessionStart(subtype, decision.sensitive))
    }
}
