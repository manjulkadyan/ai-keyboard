/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translit

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
    /** Fills the engine's user mappings for a language from storage; failures are swallowed here. */
    private val loadMappings: suspend (TranslitLang) -> Unit = {},
    /** `translit_commit` for each accepted chip; null sends nothing (tests). */
    private val analytics: TranslitAnalytics? = null,
) : SuggestionProvider {
    companion object {
        const val ProviderId = "com.manjul.aikeyboard.nlp.providers.translit"

        private fun langOf(subtype: Subtype): TranslitLang? =
            TranslitLang.entries.firstOrNull { it.tag == subtype.primaryLocale.base.language }

        /** The language of a Roman subtype (one that uses this provider), or null for any other, e.g. plain ur-PK. */
        fun romanLangOf(subtype: Subtype): TranslitLang? =
            if (subtype.nlpProviders.suggestion == ProviderId) langOf(subtype) else null

        private fun hasLatinLetter(word: String) = word.any { it in 'a'..'z' || it in 'A'..'Z' }
    }

    override val providerId = ProviderId

    /** The check `AutocorrectGate` reads on space; `en.sdict` loads on first use (decision 7). */
    val englishCheck = TranslitCandidates.englishCheck(graph)

    override val forcesSuggestionOn: Boolean = true

    override suspend fun create() {}

    override suspend fun preload(subtype: Subtype) {
        val lang = langOf(subtype) ?: return
        try {
            graph().engine(lang)
        } catch (_: IllegalStateException) {
            // No dictionary: suggestions stay off for this language rather than crash the keyboard.
        }
        try {
            loadMappings(lang)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // A database failure leaves the mappings empty; typing still works. Nothing is logged.
        }
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        // DT-O.9: no Roman strip in email, URL or password fields (fieldAllowsTranslitCommit carries the field kind).
        if (!context.allowSuggestions || !context.fieldAllowsTranslitCommit || maxCandidateCount <= 0) return emptyList()
        val lang = langOf(subtype) ?: return emptyList()
        val typed = content.composingText
        if (typed.isEmpty() || !hasLatinLetter(typed)) return emptyList()
        val g = graph()
        if (isPrivateSession) g.clearBoosts() // Decision 11: incognito forgets what was boosted before it began.
        g.englishDictionary() // Warm up for the space gate, which only reads it if loaded (also for "ok", "hi").
        val engine = try {
            g.engine(lang)
        } catch (_: IllegalStateException) {
            return emptyList()
        }
        // The Latin chip takes the last slot of the strip.
        return TranslitCandidates.build(engine, typed, lang, maxCandidateCount, g.boostFor(lang, typed), this)
    }

    /** "Save mapping" is offered only where learning is (not incognito, not a no-learning field). */
    fun canSaveMapping(): Boolean = learningAllowed()

    private fun learningAllowed() = context.allowLearning && !isPrivateSession()

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        if (candidate !is TranslitCandidate) return
        analytics?.onCommit(candidate.lang, candidate.latin) // Before the learning checks: no text is sent.
        if (candidate.latin) return
        if (!learningAllowed()) return
        graph().boost(candidate.lang, candidate.typed, candidate.text.toString())
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        if (candidate !is TranslitCandidate || candidate.latin) return
        graph().unboost(candidate.lang, candidate.typed, candidate.text.toString())
        // Only the learning is undone here. KeyboardManager's S6 hook (TranslitRevert) puts the Roman text back in
        // the host after a space commit (decision 4); the upstream revert path never edits the text itself.
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean = false

    override suspend fun getListOfWords(subtype: Subtype): List<String> = emptyList()

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double = 0.0

    override suspend fun destroy() {
        graph().clearBoosts()
    }
}
