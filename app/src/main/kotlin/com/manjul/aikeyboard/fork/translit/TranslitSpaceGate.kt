/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translit

import com.manjul.aikeyboard.core.translit.Source
import com.manjul.aikeyboard.fork.suggest.SuggestContext
import com.manjul.aikeyboard.ime.nlp.SuggestionCandidate

/**
 * Decides whether a space commits the top native candidate (plan decision 3). It reads the field rule, not the
 * autocorrect setting. Anything else returns null, and the Roman text and the space go in as typed.
 */
object TranslitSpaceGate {
    /** The host path: the field rule ([SuggestContext.fieldAllowsTranslitCommit]), then [commits]. */
    fun onSpace(
        candidate: TranslitCandidate,
        composingText: String,
        context: SuggestContext,
        english: EnglishCheck,
    ): SuggestionCandidate? {
        if (!context.fieldAllowsTranslitCommit) return null
        return if (commits(candidate, composingText, english)) candidate else null
    }

    /**
     * The gate without a field rule, shared by the host and the Translate bar: [candidate] is for [word], is a native
     * mapping or lexicon word marked for auto-commit, and is not kept as English.
     */
    fun commits(candidate: TranslitCandidate, word: String, english: EnglishCheck): Boolean {
        if (candidate.typed != word) return false
        if (candidate.latin || !candidate.isEligibleForAutoCommit) return false
        if (candidate.source != Source.MAPPING && candidate.source != Source.LEXICON) return false
        return !english.keepsLatin(word, candidate)
    }
}
