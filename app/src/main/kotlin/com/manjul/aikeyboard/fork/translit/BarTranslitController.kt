/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translit

import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.RateSampler
import com.manjul.aikeyboard.core.translit.Source
import com.manjul.aikeyboard.core.translit.TranslitLang
import com.manjul.aikeyboard.ime.editing.OwnWriteUndo
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import com.manjul.aikeyboard.ime.translateui.BarBuffer
import com.manjul.aikeyboard.ime.translateui.BarBufferTarget
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Roman typing inside the Translate bar (S6 spec "Translate bar"). While a Roman subtype is active ([lang] not null),
 * the strip follows the Roman word in progress: the text after the last space in [buffer]. Space commits the top
 * candidate under the host gate without the field rules (the bar is a plain text field, and a sensitive decision has
 * already closed it): a mapping or lexicon word, not the Latin chip, not kept as English. Backspace right after a
 * commit restores the Roman word once ([OwnWriteUndo] over a [BarBufferTarget]).
 *
 * Nothing is learned from the bar: no boost, no mapping. Main thread only, except the engine call on [compute]. The
 * Roman word and the candidates are user content: never logged, and [toString] holds no text.
 *
 * Each key function returns false when no Roman subtype is active; the caller then keeps its S2 path.
 */
class BarTranslitController(
    private val buffer: BarBuffer,
    private val graph: () -> TranslitGraph,
    private val lang: () -> TranslitLang?,
    private val decision: () -> PolicyDecision,
    analytics: (AnalyticsEvent) -> Unit,
    sampler: RateSampler,
    private val scope: CoroutineScope,
    clock: () -> Long = System::currentTimeMillis,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
) {
    private val commits = TranslitAnalytics(analytics, sampler)
    private val undo = OwnWriteUndo(BarBufferTarget(buffer), clock)
    private val englishCheck = EnglishCheck(
        english = { graph().englishIfLoaded() }, // Never loads on the space path.
        nativeFrequency = { graph().nativeFrequency(it.lang, it.text.toString()) },
    )
    private var job: Job? = null

    private val _candidates = MutableStateFlow<List<TranslitCandidate>>(emptyList())

    /** The strip for the Roman word in progress: native candidates, then the Latin chip. Empty when there is none. */
    val candidates: StateFlow<List<TranslitCandidate>> = _candidates.asStateFlow()

    /** True while a Roman subtype is active, so the strip shows [candidates] instead of host suggestions. */
    val active: Boolean get() = lang() != null

    fun onLetter(text: String): Boolean {
        val l = lang() ?: return false
        undo.invalidate()
        buffer.type(text)
        refresh(l)
        return true
    }

    fun onSpace(): Boolean {
        val l = lang() ?: return false
        val word = currentWord()
        val top = _candidates.value.firstOrNull()
        clearCandidates()
        if (top != null && commitsOnSpace(top, word)) {
            commit(top, word)
        } else {
            undo.invalidate()
            buffer.type(" ")
        }
        refresh(l)
        return true
    }

    fun onBackspace(): Boolean {
        val l = lang() ?: return false
        val restore = undo.undo()
        if (restore != null) buffer.type(restore) else buffer.backspace()
        refresh(l)
        return true
    }

    /** A strip tap. A candidate for an older word (the buffer changed since) does nothing. */
    fun onPick(candidate: TranslitCandidate) {
        val l = lang() ?: return
        val word = currentWord()
        if (candidate.typed != word || candidate.lang != l) return
        clearCandidates()
        commit(candidate, word)
    }

    /** Drops the candidates and the undo (bar closed, Enter, a paste or a word from elsewhere). */
    fun reset() {
        clearCandidates()
        undo.invalidate()
    }

    private fun commitsOnSpace(top: TranslitCandidate, word: String): Boolean {
        if (top.typed != word || top.latin || !top.isEligibleForAutoCommit) return false
        if (top.source != Source.MAPPING && top.source != Source.LEXICON) return false
        return !englishCheck.keepsLatin(word, top)
    }

    /** Replaces the Roman word with [candidate] plus a space; a native word can be undone once. */
    private fun commit(candidate: TranslitCandidate, word: String) {
        val current = buffer.text.value
        val written = candidate.text.toString() + " "
        buffer.replace(current.dropLast(word.length) + written)
        if (candidate.latin) undo.invalidate() else undo.recordCommit(written, word)
        commits.onCommit(candidate.lang, candidate.latin)
    }

    private fun currentWord(): String {
        val text = buffer.text.value
        return text.substring(text.indexOfLast { it.isWhitespace() } + 1)
    }

    private fun clearCandidates() {
        job?.cancel()
        job = null
        _candidates.value = emptyList()
    }

    private fun refresh(l: TranslitLang) {
        job?.cancel()
        val word = currentWord()
        if (word.isEmpty() || !word.all { it in 'a'..'z' || it in 'A'..'Z' } || !decision().allowSuggestionStrip) {
            job = null
            _candidates.value = emptyList()
            return
        }
        job = scope.launch {
            val out = withContext(compute) { suggest(l, word) }
            if (currentWord() == word && lang() == l) _candidates.value = out
        }
    }

    private suspend fun suggest(l: TranslitLang, word: String): List<TranslitCandidate> {
        val g = graph()
        g.englishDictionary() // Warm up for the space gate, which only reads it if loaded.
        val engine = try {
            g.engine(l)
        } catch (_: IllegalStateException) {
            return emptyList() // No dictionary: no strip, as in the host provider.
        }
        val natives = engine.suggest(word, STRIP_SIZE - 1, g.boostFor(l, word))
        val out = natives.mapIndexedTo(ArrayList(STRIP_SIZE)) { i, c ->
            TranslitCandidate(
                text = c.native,
                confidence = 1.0 / (i + 1),
                isEligibleForAutoCommit = i == 0 && (c.source == Source.MAPPING || c.source == Source.LEXICON),
                sourceProvider = null,
                typed = word,
                lang = l,
                source = c.source,
                latin = false,
            )
        }
        out += TranslitCandidate(word, 0.0, false, null, word, l, Source.RULES, latin = true)
        return out
    }

    override fun toString(): String = "BarTranslitController(candidates=${_candidates.value.size})"

    private companion object {
        /** The strip size the host asks for (S3-suggest), so the bar shows the same number of chips. */
        const val STRIP_SIZE = 3
    }
}
