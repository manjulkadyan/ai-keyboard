/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.suggest

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.manjul.aikeyboard.ime.policy.PolicyDecision

/**
 * Whether a field may be autocorrected on space (PRD FR-05, PC-1). Only plain text fields qualify: never URL, email,
 * password, number, phone or datetime fields, fields that ask for no suggestions or no personalised learning, raw
 * editors, or a field the policy marks sensitive.
 */
object FieldAutocorrectRules {
    fun allows(info: EditorInfo?, decision: PolicyDecision): Boolean =
        info != null && allows(info.inputType, info.imeOptions, decision)

    fun allows(inputType: Int, imeOptions: Int, decision: PolicyDecision): Boolean {
        if (decision.sensitive) return false
        if (imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0) return false
        // Number, phone, datetime and TYPE_NULL (raw editors) are all excluded.
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        if (inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0) return false
        return when (inputType and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            -> false
            else -> true
        }
    }
}
