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
    fun onSpace(
        candidate: TranslitCandidate,
        composingText: String,
        context: SuggestContext,
        english: EnglishCheck,
    ): SuggestionCandidate? {
        if (!context.fieldAllowsTranslitCommit) return null
        if (candidate.typed != composingText) return null
        if (candidate.latin || !candidate.isEligibleForAutoCommit) return null
        if (candidate.source != Source.MAPPING && candidate.source != Source.LEXICON) return null
        if (english.keepsLatin(composingText, candidate)) return null
        return candidate
    }
}
