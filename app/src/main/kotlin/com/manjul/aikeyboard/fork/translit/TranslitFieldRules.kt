/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translit

import android.text.InputType
import com.manjul.aikeyboard.ime.policy.PolicyDecision

/**
 * Whether a field may commit a native candidate on space (plan decision 8). The same field kinds as
 * `FieldAutocorrectRules`. This function does not read [imeOptions]. In practice `IME_FLAG_NO_PERSONALIZED_LEARNING`
 * still blocks the commit, because `SensitiveFieldPolicy` marks such a field sensitive and the decision says so.
 */
object TranslitFieldRules {
    @Suppress("UNUSED_PARAMETER") // imeOptions is part of the contract; the decision carries its effect.
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
