/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.policy

import com.manjul.aikeyboard.ime.policy.InputTypeBits as Bits

/**
 * The hard gate that decides which keyboard features may run in the current input field
 * (TRD 4.1.2). Pure function, no Android dependencies.
 *
 * If any sensitive reason applies, every capability is off and analytics are minimal. Otherwise
 * the soft rules (suggestion flag, field class, card heuristic) only turn capabilities off and
 * combine with AND.
 */
object SensitiveFieldPolicy {
    fun evaluate(field: FieldDescriptor, ctx: PolicyContext): PolicyDecision {
        val cls = field.inputType and Bits.MASK_CLASS
        val variation = field.inputType and Bits.MASK_VARIATION
        val isText = cls == Bits.CLASS_TEXT

        val reasons = buildSet {
            if (isText && variation in textPasswordVariations) add(SensitivityReason.PASSWORD)
            if (cls == Bits.CLASS_NUMBER && variation == Bits.NUMBER_VARIATION_PASSWORD) {
                add(SensitivityReason.NUMBER_PASSWORD)
            }
            if (field.imeOptions and Bits.IME_FLAG_NO_PERSONALIZED_LEARNING != 0) {
                add(SensitivityReason.NO_PERSONALIZED_LEARNING)
            }
            if (ctx.incognitoManual) add(SensitivityReason.INCOGNITO_MANUAL)
            if (ctx.deviceLocked) add(SensitivityReason.DEVICE_LOCKED)
            val pkg = field.packageName
            if (pkg != null && pkg in ctx.privatePackages) add(SensitivityReason.PRIVATE_APP)
            if (pkg != null && pkg in ctx.forceSensitivePackages) add(SensitivityReason.FORCED_PACKAGE)
        }
        val card = CardFieldHeuristic.matches(field)
        val allReasons = if (card) reasons + SensitivityReason.CARD_HEURISTIC else reasons

        if (reasons.isNotEmpty()) {
            return PolicyDecision.Locked.copy(reasons = allReasons)
        }

        var d = PolicyDecision.Unrestricted.copy(reasons = allReasons)
        if (isText && field.inputType and Bits.TEXT_FLAG_NO_SUGGESTIONS != 0) {
            d = d.copy(allowSuggestionStrip = false)
        }
        if (cls == Bits.CLASS_PHONE || cls == Bits.CLASS_DATETIME ||
            (isText && variation == Bits.TEXT_VARIATION_URI)
        ) {
            d = d.copy(allowTranslate = false, allowAi = false)
        }
        if (isText && variation in textEmailVariations) {
            d = d.copy(allowTranslate = false, allowAi = false, allowLearning = false, allowSuggestionHistory = false)
        }
        if (card) {
            d = d.copy(
                allowLearning = false, allowTranslate = false, allowAi = false,
                allowCloud = false, allowVoice = false, allowClipboardHistory = false,
            )
        }
        return d
    }

    private val textPasswordVariations = setOf(
        Bits.TEXT_VARIATION_PASSWORD,
        Bits.TEXT_VARIATION_VISIBLE_PASSWORD,
        Bits.TEXT_VARIATION_WEB_PASSWORD,
    )

    private val textEmailVariations = setOf(
        Bits.TEXT_VARIATION_EMAIL_ADDRESS,
        Bits.TEXT_VARIATION_WEB_EMAIL_ADDRESS,
    )
}
