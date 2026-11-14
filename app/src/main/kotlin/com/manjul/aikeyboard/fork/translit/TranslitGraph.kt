/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translit

import com.manjul.aikeyboard.core.suggest.DictionaryFormat
import com.manjul.aikeyboard.core.suggest.SuggestDictionary
import com.manjul.aikeyboard.core.translit.CustomMappings
import com.manjul.aikeyboard.core.translit.RuleTable
import com.manjul.aikeyboard.core.translit.SdictLexicon
import com.manjul.aikeyboard.core.translit.TranslitEngine
import com.manjul.aikeyboard.core.translit.TranslitLang
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * App-scoped and lazy. Loads one engine per language on first use and keeps it. Also holds the in-memory boosts
 * (decision 11): at most [MAX_BOOSTS] entries, keyed by language and Roman word, lost with the process. Roman words and
 * native words are user content: nothing here logs them.
 */
open class TranslitGraph(
    private val openAsset: (String) -> InputStream,
    private val io: CoroutineDispatcher,
) {
    val mappings: Map<TranslitLang, CustomMappings> = TranslitLang.entries.associateWith { CustomMappings() }

    private val engines = HashMap<TranslitLang, TranslitEngine>()
    private val lexicons = ConcurrentHashMap<TranslitLang, SdictLexicon>()
    private val englishLock = Any()
    private var english: SuggestDictionary? = null
    private var englishTried = false
    private val loadMutex = Mutex()

    private val boostLock = Any()
    private val boosts = object : LinkedHashMap<String, MutableMap<String, Int>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MutableMap<String, Int>>?) =
            size > MAX_BOOSTS
    }

    open suspend fun engine(lang: TranslitLang): TranslitEngine = loadMutex.withLock {
        engines[lang] ?: withContext(io) { load(lang) }.also { engines[lang] = it }
    }

    private fun load(lang: TranslitLang): TranslitEngine {
        val lexicon = try {
            openAsset("dict/${lang.tag}.sdict").buffered().use { SdictLexicon.read(it) }
        } catch (_: IOException) {
            throw IllegalStateException("Dictionary missing for ${lang.tag}") // No typed text in the message.
        } catch (_: IllegalArgumentException) {
            throw IllegalStateException("Dictionary unreadable for ${lang.tag}") // Corrupt or newer file.
        }
        lexicons[lang] = lexicon
        return TranslitEngine(lang, RuleTable.load(lang), lexicon, mappings.getValue(lang))
    }

    /** The native lexicon frequency of [word] once the engine for [lang] is loaded, else null. */
    fun nativeFrequency(lang: TranslitLang, word: String): Int? = lexicons[lang]?.frequency(word)

    /**
     * `en.sdict`, loaded once on first use (decision 7). Null when it is missing or unreadable; that is remembered, so
     * a bad file is not re-read on every word. Blocking: the provider warms it off the main thread.
     */
    open fun englishDictionary(): SuggestDictionary? = synchronized(englishLock) {
        if (!englishTried) {
            englishTried = true
            english = try {
                openAsset("dict/en.sdict").buffered().use { DictionaryFormat.read(it) }
            } catch (_: IOException) {
                null
            } catch (_: RuntimeException) {
                null // Corrupt or newer file (IllegalArgumentException) or an unusable source. No text kept.
            }
        }
        english
    }

    /** One more use of [native] for [roman]. */
    fun boost(lang: TranslitLang, roman: String, native: String) = synchronized(boostLock) {
        val perWord = boosts.getOrPut(key(lang, roman)) { HashMap() }
        perWord[native] = minOf((perWord[native] ?: 0) + 1, MAX_USES)
    }

    /** Forgets the boost of one pick, after a revert. */
    fun unboost(lang: TranslitLang, roman: String, native: String) = synchronized(boostLock) {
        val k = key(lang, roman)
        boosts[k]?.let { it.remove(native); if (it.isEmpty()) boosts.remove(k) }
    }

    /** The boost lookup for [roman], in the ranker's units. */
    fun boostFor(lang: TranslitLang, roman: String): (String) -> Int {
        val copy = synchronized(boostLock) { boosts[key(lang, roman)]?.toMap() } ?: return { 0 }
        return { native -> copy[native] ?: 0 }
    }

    fun boostCount(): Int = synchronized(boostLock) { boosts.size }

    fun clearBoosts() = synchronized(boostLock) { boosts.clear() }

    private fun key(lang: TranslitLang, roman: String) = lang.tag + ':' + roman.lowercase(Locale.ROOT)

    override fun toString(): String = "TranslitGraph(engines=${engines.size})"

    companion object {
        const val MAX_BOOSTS = 256
        private const val MAX_USES = 3
    }
}
