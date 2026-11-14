/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translit

import com.manjul.aikeyboard.core.translit.CustomMappings
import com.manjul.aikeyboard.core.translit.RuleTable
import com.manjul.aikeyboard.core.translit.SdictLexicon
import com.manjul.aikeyboard.core.translit.TranslitEngine
import com.manjul.aikeyboard.core.translit.TranslitLang
import java.io.IOException
import java.io.InputStream
import java.util.Locale
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
        } catch (e: IOException) {
            throw IllegalStateException("Dictionary missing for ${lang.tag}") // No typed text in the message.
        }
        return TranslitEngine(lang, RuleTable.load(lang), lexicon, mappings.getValue(lang))
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
