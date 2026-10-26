/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.suggest

import androidx.compose.ui.graphics.vector.ImageVector
import com.manjul.aikeyboard.core.config.ConfigKeys
import com.manjul.aikeyboard.core.config.DefaultsRemoteConfig
import com.manjul.aikeyboard.core.database.UserWordRepository
import com.manjul.aikeyboard.core.datastore.FeatureSettings
import com.manjul.aikeyboard.core.suggest.AutocorrectPolicy
import com.manjul.aikeyboard.core.suggest.DictionaryFormat
import com.manjul.aikeyboard.core.suggest.KeyProximity
import com.manjul.aikeyboard.core.suggest.LearnedWords
import com.manjul.aikeyboard.core.suggest.SuggestDictionary
import com.manjul.aikeyboard.core.suggest.SuggestEngine
import com.manjul.aikeyboard.ime.nlp.SpellingResult
import com.manjul.aikeyboard.ime.nlp.SuggestionCandidate
import com.manjul.aikeyboard.ime.nlp.SuggestionProvider
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A word candidate from [LatinSuggester]. [typed] and [previousWord] are what the candidate was computed for, so
 * learning records the right bigram and the space key can check the candidate still matches the composing word.
 * User content: never logged ([toString] is overridden to hold no text).
 */
class LatinWordCandidate(
    override val text: CharSequence,
    override val confidence: Double,
    override val isEligibleForAutoCommit: Boolean,
    override val sourceProvider: SuggestionProvider?,
    val typed: String,
    val previousWord: String?,
    val language: String,
) : SuggestionCandidate {
    override val secondaryText: CharSequence? = null
    override val isEligibleForUserRemoval: Boolean = true
    override val icon: ImageVector? = null

    override fun toString(): String = "LatinWordCandidate(autoCommit=$isEligibleForAutoCommit)"
}

/**
 * The fork's suggestion engine behind upstream `LatinLanguageProvider` (PRD FR-05, decision 4).
 *
 * - One dictionary at a time: [preload] loads `dict/<lang>.sdict` for the active subtype on [io] and drops the
 *   previous one first. Until it is ready [suggest] returns nothing; it never blocks the typing path.
 * - Autocorrect: the first candidate is eligible for auto-commit only when [AutocorrectPolicy] agrees, with the
 *   language in `autocorrectLocales`, the field allowing it ([SuggestContext]) and the word not recently reverted.
 * - Learning gate (PC-1): accepted words are recorded only when [SuggestContext.allowLearning] and not
 *   [isPrivateSession], checked at accept time and again when a held autocorrection is written. An autocorrection
 *   is held until the next accept so that a revert (backspace) drops it unlearned; it is dropped too when the field
 *   changes or stops allowing learning before then ([SuggestContext.epoch]).
 *
 * Typed text is user content: nothing here logs it or puts it in an exception.
 */
class LatinSuggester(
    private val openAsset: (String) -> InputStream,
    private val learned: () -> UserWordRepository,
    private val settings: () -> FeatureSettings,
    private val context: SuggestContext,
    private val isPrivateSession: () -> Boolean,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** `autocorrect_min_score` (decision 4), a `:core:config` default. */
    private val minScore: Double = DefaultsRemoteConfig().double(ConfigKeys.AUTOCORRECT_MIN_SCORE),
) {
    private class Loaded(val language: String, val dict: SuggestDictionary, val engine: SuggestEngine)

    @Volatile private var loaded: Loaded? = null
    private val loadMutex = Mutex()

    private val lock = Any()
    private var pending: LatinWordCandidate? = null
    private var pendingEpoch: Int = 0
    private val recentlyReverted = object : LinkedHashMap<String, Unit>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > MAX_REVERTED
    }

    /** The language of the dictionary held now, or null while none is (loading, or no dictionary for it). */
    val loadedLanguage: String? get() = loaded?.language

    suspend fun preload(language: String) = loadMutex.withLock {
        if (loaded?.language == language) return@withLock
        flushPending()
        loaded = null // Release the previous dictionary before reading the next one.
        synchronized(lock) { recentlyReverted.clear() }
        val dict = withContext(io) { readDictionary(language) } ?: return@withLock
        val repo = learned()
        try {
            repo.load(language)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The snapshot stays empty: suggestions still work, unlearned. Nothing is logged (user content).
        }
        loaded = Loaded(language, dict, SuggestEngine(dict, KeyProximity.forLocale(language), UseCounts(repo)))
    }

    private fun readDictionary(language: String): SuggestDictionary? =
        try {
            openAsset("dict/$language.sdict").buffered().use { DictionaryFormat.read(it) }
        } catch (_: IOException) {
            null // No dictionary for this language.
        } catch (_: IllegalArgumentException) {
            null // A corrupt or newer file: suggestions stay off rather than crash the keyboard.
        }

    fun suggest(
        language: String,
        typed: String,
        previousWord: String?,
        max: Int,
        source: SuggestionProvider,
    ): List<SuggestionCandidate> {
        if (!context.allowSuggestions) return emptyList()
        val current = settings()
        if (!current.suggestionsEnabled) return emptyList()
        val l = loaded?.takeIf { it.language == language } ?: return emptyList()
        if (typed.isBlank()) return emptyList()
        val candidates = l.engine.suggest(typed, previousWord, max)
        val reverted = synchronized(lock) { recentlyReverted.containsKey(typed.lowercase()) }
        val eligible = AutocorrectPolicy.shouldAutocorrect(
            typed = typed,
            top = candidates.firstOrNull(),
            minScore = minScore,
            localeEnabled = language in current.autocorrectLocales,
            fieldAllows = context.fieldAllowsAutocorrect,
            recentlyReverted = reverted,
        )
        val prev = previousWord?.lowercase()
        return candidates.mapIndexed { i, c ->
            LatinWordCandidate(
                text = c.word,
                confidence = c.score,
                isEligibleForAutoCommit = i == 0 && eligible,
                sourceProvider = source,
                typed = typed,
                previousWord = prev,
                language = language,
            )
        }
    }

    private fun learningAllowed(): Boolean = context.allowLearning && !isPrivateSession()

    fun onAccepted(candidate: SuggestionCandidate) {
        if (candidate !is LatinWordCandidate) return
        flushPending()
        if (!learningAllowed()) return
        if (candidate.isEligibleForAutoCommit) {
            synchronized(lock) {
                pending = candidate
                pendingEpoch = context.epoch
            }
        } else {
            record(candidate)
        }
    }

    fun onReverted(candidate: SuggestionCandidate) {
        if (candidate !is LatinWordCandidate) return
        synchronized(lock) {
            if (pending === candidate) pending = null
            recentlyReverted[candidate.typed.lowercase()] = Unit
        }
    }

    fun onRemoved(candidate: SuggestionCandidate): Boolean {
        if (candidate !is LatinWordCandidate) return false
        // Blocking stores the word on this phone, which counts as learning under PC-1.
        if (!learningAllowed()) return false
        learned().block(candidate.language, candidate.text.toString().lowercase())
        return true
    }

    private fun flushPending() {
        var epoch = 0
        val held = synchronized(lock) { pending.also { pending = null; epoch = pendingEpoch } } ?: return
        // Written only from the same field, still allowed to learn; otherwise dropped unlearned.
        if (epoch == context.epoch && learningAllowed()) record(held)
    }

    private fun record(candidate: LatinWordCandidate) {
        learned().recordAccepted(candidate.language, candidate.text.toString(), candidate.previousWord)
    }

    fun spell(language: String, word: String, max: Int): SpellingResult {
        val l = loaded?.takeIf { it.language == language } ?: return SpellingResult.validWord()
        if (word.isBlank() || word.any { !isWordChar(it) }) {
            return SpellingResult.validWord()
        }
        if (l.dict.containsExact(word) || learned().boost(language, word.lowercase()) > 0) {
            return SpellingResult.validWord()
        }
        val suggestions = l.engine.suggest(word, null, max).filter { !it.isTypedWord }.map { it.word }
        return SpellingResult.typo(suggestions.toTypedArray())
    }

    fun words(language: String): List<String> =
        loaded?.takeIf { it.language == language }?.dict?.wordList().orEmpty()

    fun frequency(language: String, word: String): Double =
        loaded?.takeIf { it.language == language }?.dict?.frequency(word)?.div(255.0) ?: 0.0

    /**
     * [UserWordRepository.boost] is on the 0-255 frequency scale (`min(count, 20) * 6`), while [SuggestEngine]
     * reads [LearnedWords] values as use counts. This turns the boost back into a use count.
     */
    private class UseCounts(private val repo: UserWordRepository) : LearnedWords {
        override fun boost(locale: String, word: String): Int = repo.boost(locale, word) / UserWordRepository.BOOST_PER_USE
        override fun bigram(locale: String, prev: String, word: String): Int = repo.bigram(locale, prev, word)
        override fun isBlocked(locale: String, word: String): Boolean = repo.isBlocked(locale, word)
    }

    companion object {
        private const val MAX_REVERTED = 64

        /** Letters, combining marks (Devanagari matras, Arabic harakat), apostrophes and ZWNJ/ZWJ. */
        private fun isWordChar(c: Char): Boolean =
            c.isLetter() || c == '\'' || c == '\u2019' || c == '\u200C' || c == '\u200D' ||
                when (Character.getType(c).toByte()) {
                    Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK -> true
                    else -> false
                }

        /** The dictionary key for a subtype locale: its language only, with legacy codes ("in") mapped ("id"). */
        fun languageOf(locale: Locale): String = locale.toLanguageTag().substringBefore('-').lowercase()

        /**
         * The word before the composing word in [textBeforeComposing], or null at the start of a sentence or text.
         */
        fun previousWord(textBeforeComposing: String): String? {
            val trimmed = textBeforeComposing.trimEnd()
            if (trimmed.isEmpty() || trimmed.length == textBeforeComposing.length) return null
            if (!isWordChar(trimmed.last())) return null
            val start = trimmed.indexOfLast { !isWordChar(it) } + 1
            return trimmed.substring(start).takeIf { it.isNotEmpty() }
        }
    }
}
