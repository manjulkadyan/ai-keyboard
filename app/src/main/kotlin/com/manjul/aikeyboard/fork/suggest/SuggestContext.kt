/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.suggest

import android.view.inputmethod.EditorInfo
import com.manjul.aikeyboard.ime.policy.PolicyDecision

/**
 * The facts about the focused field that the suggestion provider needs (PC-1). `FlorisImeService` sets it in
 * `onStartInput` and on every policy decision; the provider reads it on the typing path. Everything is denied until
 * the first field and after [clear], so a missed update fails closed.
 */
class SuggestContext {
    @Volatile var fieldAllowsAutocorrect: Boolean = false
    @Volatile var allowLearning: Boolean = false
    @Volatile var allowSuggestions: Boolean = false

    /** Learned words and bigrams may shape the ranking (PolicyDecision.allowSuggestionHistory, TRD §4.1.2). */
    @Volatile var allowSuggestionHistory: Boolean = false

    /**
     * Changes when a field starts or ends and whenever learning is turned off, so learning held back for a field
     * ([LatinSuggester]'s pending autocorrection) can tell it is no longer in the same, still-learning field.
     */
    @Volatile var epoch: Int = 0
        private set

    // Only the type bits of the field are kept, never its text, hint or package.
    @Volatile private var inputType: Int = 0
    @Volatile private var imeOptions: Int = 0
    @Volatile private var hasField: Boolean = false

    /** A field started ([info] null means none). */
    fun update(info: EditorInfo?, decision: PolicyDecision) {
        epoch++
        hasField = info != null
        inputType = info?.inputType ?: 0
        imeOptions = info?.imeOptions ?: 0
        onDecision(decision)
    }

    /** The decision for the current field changed (settings arrived, incognito toggled, field closed). */
    fun onDecision(decision: PolicyDecision) {
        fieldAllowsAutocorrect = hasField && FieldAutocorrectRules.allows(inputType, imeOptions, decision)
        val learning = hasField && decision.allowLearning
        if (allowLearning && !learning) epoch++
        allowLearning = learning
        allowSuggestions = hasField && decision.allowSuggestionStrip
        allowSuggestionHistory = hasField && decision.allowSuggestionHistory
    }

    fun clear() {
        epoch++
        hasField = false
        inputType = 0
        imeOptions = 0
        fieldAllowsAutocorrect = false
        allowLearning = false
        allowSuggestions = false
        allowSuggestionHistory = false
    }
}
