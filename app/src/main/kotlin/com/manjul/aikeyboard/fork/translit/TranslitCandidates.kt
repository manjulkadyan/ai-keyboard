/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translit

import com.manjul.aikeyboard.core.translit.Source
import com.manjul.aikeyboard.core.translit.TranslitEngine
import com.manjul.aikeyboard.core.translit.TranslitLang
import com.manjul.aikeyboard.ime.nlp.SuggestionProvider

/**
 * The one strip builder for Roman typing, used by the host provider and the Translate bar, so both show the same
 * candidates for the same word. The Roman word and the candidates are user content: nothing here logs them.
 */
object TranslitCandidates {
    /** The strip size the host asks for (S3-suggest); the bar uses it too. */
    const val STRIP_SIZE = 3

    /**
     * At most [stripSize] candidates for [typed]: the native candidates, then the Latin chip in the last slot. Only
     * the top native candidate, and only for a mapping or a lexicon word, may auto-commit (spec).
     */
    fun build(
        engine: TranslitEngine,
        typed: String,
        lang: TranslitLang,
        stripSize: Int,
        boost: (String) -> Int,
        provider: SuggestionProvider?,
    ): List<TranslitCandidate> {
        if (stripSize <= 0) return emptyList()
        val nativeMax = stripSize - 1
        val natives = if (nativeMax > 0) engine.suggest(typed, nativeMax, boost) else emptyList()
        val out = ArrayList<TranslitCandidate>(natives.size + 1)
        natives.forEachIndexed { i, c ->
            out += TranslitCandidate(
                text = c.native,
                confidence = 1.0 / (i + 1),
                isEligibleForAutoCommit = i == 0 && (c.source == Source.MAPPING || c.source == Source.LEXICON),
                sourceProvider = provider,
                typed = typed,
                lang = lang,
                source = c.source,
                latin = false,
            )
        }
        out += TranslitCandidate(
            text = typed,
            confidence = 0.0,
            isEligibleForAutoCommit = false,
            sourceProvider = provider,
            typed = typed,
            lang = lang,
            source = Source.RULES,
            latin = true,
        )
        return out
    }

    /** The English check both paths use on space. It never loads `en.sdict`: it reads it only if a warm-up did. */
    fun englishCheck(graph: () -> TranslitGraph) = EnglishCheck(
        english = { graph().englishIfLoaded() },
        nativeFrequency = { graph().nativeFrequency(it.lang, it.text.toString()) },
    )
}
