/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.policy

enum class SensitivityReason {
    PASSWORD,
    NUMBER_PASSWORD,
    NO_PERSONALIZED_LEARNING,
    INCOGNITO_MANUAL,
    DEVICE_LOCKED,
    PRIVATE_APP,
    FORCED_PACKAGE,
    CARD_HEURISTIC,
}

/** What the keyboard may do in the current field (TRD 4.1.2). Controllers re-check this before acting. */
data class PolicyDecision(
    val sensitive: Boolean,
    val reasons: Set<SensitivityReason>,
    val allowLearning: Boolean,
    val allowSuggestionHistory: Boolean,
    val allowSuggestionStrip: Boolean,
    val allowClipboardHistory: Boolean,
    val allowTranslate: Boolean,
    val allowVoice: Boolean,
    val allowAi: Boolean,
    val allowCloud: Boolean,
    val analyticsMinimal: Boolean,
) {
    /** True if any capability allowed in [other] is not allowed here. */
    fun isMoreRestrictiveThan(other: PolicyDecision): Boolean =
        (other.allowLearning && !allowLearning) ||
            (other.allowSuggestionHistory && !allowSuggestionHistory) ||
            (other.allowSuggestionStrip && !allowSuggestionStrip) ||
            (other.allowClipboardHistory && !allowClipboardHistory) ||
            (other.allowTranslate && !allowTranslate) ||
            (other.allowVoice && !allowVoice) ||
            (other.allowAi && !allowAi) ||
            (other.allowCloud && !allowCloud)

    companion object {
        val Unrestricted = PolicyDecision(
            sensitive = false, reasons = emptySet(),
            allowLearning = true, allowSuggestionHistory = true, allowSuggestionStrip = true,
            allowClipboardHistory = true, allowTranslate = true, allowVoice = true,
            allowAi = true, allowCloud = true, analyticsMinimal = false,
        )

        val Locked = PolicyDecision(
            sensitive = true, reasons = setOf(SensitivityReason.DEVICE_LOCKED),
            allowLearning = false, allowSuggestionHistory = false, allowSuggestionStrip = false,
            allowClipboardHistory = false, allowTranslate = false, allowVoice = false,
            allowAi = false, allowCloud = false, analyticsMinimal = true,
        )
    }
}
