/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.suggest

import com.manjul.aikeyboard.ime.nlp.SuggestionCandidate

/**
 * The last check before `KeyboardManager` auto-commits a candidate (PRD FR-05, decision 4). Our autocorrect fires on
 * space only, re-checks the live field facts, and only for the composing word it was computed for: a candidate
 * list that is late by one keystroke never replaces the word. Candidates of other providers pass unchanged.
 */
object AutocorrectGate {
    fun onSpace(candidate: SuggestionCandidate?, composingText: String, context: SuggestContext): SuggestionCandidate? {
        if (candidate !is LatinWordCandidate) return candidate
        return candidate.takeIf {
            it.isEligibleForAutoCommit && context.fieldAllowsAutocorrect && it.typed == composingText
        }
    }

    /** Punctuation, media keys and other non-space triggers never apply our autocorrect. */
    fun onOtherKey(candidate: SuggestionCandidate?): SuggestionCandidate? =
        candidate.takeUnless { it is LatinWordCandidate }
}
