/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translit

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.ime.nlp.SuggestionCandidate
import org.florisboard.lib.compose.stringRes

/**
 * The Latin chip of a Roman strip reads as "<word>, Keep the Roman spelling" in a screen reader, so the user hears
 * that a tap keeps the Roman text. Every other candidate is left as it is. The word stays on screen only.
 */
@Composable
fun Modifier.latinChipSemantics(candidate: SuggestionCandidate): Modifier {
    if (candidate !is TranslitCandidate || !candidate.latin) return this
    val description = stringRes(R.string.translit__latin_chip_description, "word" to candidate.text.toString())
    return semantics(mergeDescendants = true) { contentDescription = description }
}
