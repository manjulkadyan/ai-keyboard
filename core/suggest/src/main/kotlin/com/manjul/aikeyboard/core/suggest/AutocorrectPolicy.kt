/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.suggest

/**
 * Whether pressing space replaces [typed] with [top] (PRD FR-05, PC-1). Conservative by design: anything that
 * looks like a handle, path, URL, code or number, ALL CAPS, very short input, a known word, or a word the user just
 * reverted is left alone. [fieldAllows] is false for URL, email, password, number, phone and datetime fields and
 * with IME_FLAG_NO_PERSONALIZED_LEARNING; the caller decides it.
 */
object AutocorrectPolicy {
    fun shouldAutocorrect(
        typed: String,
        top: Candidate?,
        minScore: Double,
        localeEnabled: Boolean,
        fieldAllows: Boolean,
        recentlyReverted: Boolean,
    ): Boolean {
        if (!localeEnabled || !fieldAllows || recentlyReverted) return false
        if (typed.length < 2) return false
        if (typed.any { it.isDigit() || it == '@' || it == '/' || it == '_' }) return false
        // A dot anywhere but the last position ("e.g", ".net", "a.b") means an abbreviation, domain or code.
        if (typed.dropLast(1).contains('.')) return false
        if (isAllCaps(typed)) return false
        if (top == null || top.isTypedWord || top.score < minScore) return false
        // The typed word itself is a dictionary or learned word: the engine then ranks it first as isTypedWord.
        if (top.word.equals(typed, ignoreCase = true)) return false
        return true
    }
}
