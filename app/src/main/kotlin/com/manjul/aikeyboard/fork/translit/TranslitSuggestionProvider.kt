/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translit

import com.manjul.aikeyboard.core.translit.Source
import com.manjul.aikeyboard.core.translit.TranslitLang
import com.manjul.aikeyboard.fork.suggest.SuggestContext
import com.manjul.aikeyboard.ime.core.Subtype
import com.manjul.aikeyboard.ime.editor.EditorContent
import com.manjul.aikeyboard.ime.nlp.SuggestionCandidate
import com.manjul.aikeyboard.ime.nlp.SuggestionProvider

/**
 * Suggestions for the Roman subtypes (`ur-PK-roman`, `hi-IN-roman`): native candidates for the composing Roman word,
 * then the Latin chip. It never touches `LatinSuggester`. No network. Typed text is user content: nothing is logged.
 */
class TranslitSuggestionProvider(
    private val graph: () -> TranslitGraph,
    private val context: SuggestContext,
    private val isPrivateSession: () -> Boolean,
) : SuggestionProvider {
    companion object {
        const val ProviderId = "com.manjul.aikeyboard.nlp.providers.translit"

        private fun langOf(subtype: Subtype): TranslitLang? =
            TranslitLang.entries.firstOrNull { it.tag == subtype.primaryLocale.base.language }

        private fun hasLatinLetter(word: String) = word.any { it in 'a'..'z' || it in 'A'..'Z' }
    }

    override val providerId = ProviderId

    /** The check `AutocorrectGate` reads on space; `en.sdict` loads on first use (decision 7). */
    val englishCheck = EnglishCheck(
        english = { graph().englishIfLoaded() }, // Never loads on the space path.
        nativeFrequency = { graph().nativeFrequency(it.lang, it.text.toString()) },
    )

    override val forcesSuggestionOn: Boolean = true

    override suspend fun create() {}

    override suspend fun preload(subtype: Subtype) {
        val lang = langOf(subtype) ?: return
        try {
            graph().engine(lang)
        } catch (_: IllegalStateException) {
            // No dictionary: suggestions stay off for this language rather than crash the keyboard.
        }
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        if (!context.allowSuggestions || maxCandidateCount <= 0) return emptyList()
        val lang = langOf(subtype) ?: return emptyList()
        val typed = content.composingText
        if (typed.isEmpty() || !hasLatinLetter(typed)) return emptyList()
        val g = graph()
        g.englishDictionary() // Warm up for the space gate, which only reads it if loaded (also for "ok", "hi").
        val engine = try {
            g.engine(lang)
        } catch (_: IllegalStateException) {
            return emptyList()
        }
        // The Latin chip takes the last slot of the strip.
        val nativeMax = maxCandidateCount - 1
        val natives = if (nativeMax > 0) engine.suggest(typed, nativeMax, g.boostFor(lang, typed)) else emptyList()
        val out = ArrayList<SuggestionCandidate>(natives.size + 1)
        natives.forEachIndexed { i, c ->
            out += TranslitCandidate(
                text = c.native,
                confidence = 1.0 / (i + 1),
                // Only the top candidate, and only for a mapping or a lexicon word (spec).
                isEligibleForAutoCommit = i == 0 && (c.source == Source.MAPPING || c.source == Source.LEXICON),
                sourceProvider = this,
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
            sourceProvider = this,
            typed = typed,
            lang = lang,
            source = Source.RULES,
            latin = true,
        )
        return out
    }

    /** "Save mapping" is offered only where learning is (not incognito, not a no-learning field). */
    fun canSaveMapping(): Boolean = learningAllowed()

    private fun learningAllowed() = context.allowLearning && !isPrivateSession()

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        if (candidate !is TranslitCandidate || candidate.latin) return
        if (!learningAllowed()) return
        graph().boost(candidate.lang, candidate.typed, candidate.text.toString())
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        if (candidate !is TranslitCandidate || candidate.latin) return
        graph().unboost(candidate.lang, candidate.typed, candidate.text.toString())
        // The upstream revert path puts the Roman text back (decision 4).
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean = false

    override suspend fun getListOfWords(subtype: Subtype): List<String> = emptyList()

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double = 0.0

    override suspend fun destroy() {
        graph().clearBoosts()
    }
}
