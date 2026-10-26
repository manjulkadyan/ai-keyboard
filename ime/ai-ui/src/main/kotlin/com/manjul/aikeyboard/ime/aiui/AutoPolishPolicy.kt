/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.aiui

import com.manjul.aikeyboard.ime.policy.PolicyDecision

/**
 * Whether a dictation is polished without a tap (PRD FR-16 AC5/AC6, plan decision 13): only with the toggle on, the
 * `consent_auto_polish` consent on record, Pro, a field that allows AI and cloud and is not sensitive, and the
 * auto-polish quota pause not in force.
 */
object AutoPolishPolicy {
    fun shouldAutoRun(
        autoPolish: Boolean,
        consentAt: Long,
        isPro: Boolean,
        decision: PolicyDecision,
        quotaPaused: Boolean,
    ): Boolean = autoPolish &&
        consentAt > 0L &&
        isPro &&
        decision.allowAi &&
        decision.allowCloud &&
        !decision.sensitive &&
        !quotaPaused
}
