/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translit

import androidx.compose.ui.graphics.vector.ImageVector
import com.manjul.aikeyboard.core.translit.Source
import com.manjul.aikeyboard.core.translit.TranslitLang
import com.manjul.aikeyboard.ime.nlp.SuggestionCandidate
import com.manjul.aikeyboard.ime.nlp.SuggestionProvider

/**
 * A Roman-to-native candidate, or the Latin chip ([latin] true, [text] is the typed Roman word). [typed] is the
 * Roman word it was computed for. User content: [toString] holds no text.
 */
class TranslitCandidate(
    override val text: CharSequence,
    override val confidence: Double,
    /** True only for a user mapping or a lexicon word (spec); rule-only output and the Latin chip never auto-commit. */
    override val isEligibleForAutoCommit: Boolean,
    override val sourceProvider: SuggestionProvider?,
    val typed: String,
    val lang: TranslitLang,
    val source: Source,
    val latin: Boolean,
) : SuggestionCandidate {
    override val secondaryText: CharSequence? = null
    override val isEligibleForUserRemoval: Boolean = false
    override val icon: ImageVector? = null

    override fun toString(): String = "TranslitCandidate(lang=${lang.tag}, source=$source, latin=$latin)"
}
