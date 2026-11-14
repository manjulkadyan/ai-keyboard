/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translit

import android.text.InputType
import com.manjul.aikeyboard.ime.policy.PolicyDecision

/**
 * Whether a field may commit a native candidate on space (plan decision 8). The same field kinds as
 * `FieldAutocorrectRules`, except that `IME_FLAG_NO_PERSONALIZED_LEARNING` does not block it: that flag blocks
 * learning only.
 */
object TranslitFieldRules {
    @Suppress("UNUSED_PARAMETER") // imeOptions is kept for the contract: no option bit blocks the commit.
    fun allowsSpaceCommit(inputType: Int, imeOptions: Int, decision: PolicyDecision): Boolean {
        if (decision.sensitive || !decision.allowSuggestionStrip) return false
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
