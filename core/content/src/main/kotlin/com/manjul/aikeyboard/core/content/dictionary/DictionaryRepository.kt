/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.dictionary

import com.manjul.aikeyboard.core.ai.kb.DictionaryClient
import com.manjul.aikeyboard.core.ai.kb.DictionaryResult
import com.manjul.aikeyboard.core.analytics.AnalyticsSink
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.content.text.HeadwordNorm
import com.manjul.aikeyboard.core.database.DictionaryDao
import com.manjul.aikeyboard.core.database.DictionaryEntryEntity
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * The answer to one query. [offline] is true when no online answer (fresh or cached) was used (plan F16).
 * [packMissing] is true when no dictionary pack is installed, so the UI can offer the download.
 */
data class LookupResult(
    val definitions: List<Definition>,
    val offline: Boolean,
    val attribution: Attribution?,
    val packMissing: Boolean = false,
) {
    override fun toString(): String =
        "LookupResult(definitions=${definitions.size}, offline=$offline, packMissing=$packMissing)"
}

/**
 * English definitions: the offline pack first, then the online cache, then kb-api. kb-api is called only from
 * [lookup] with `allowNetwork`, `onlineDictionary` on and AI consent given (plan decision 6, ruling F1). Online answers
 * are cached in `dictionary_entry` for [CACHE_TTL_MS]. Each query logs one `dictionary_lookup`. Lookups never throw and
 * never log: the word is user content (F17).
 */
class DictionaryRepository(
    private val reader: () -> DictionaryPackReader?,
    private val dao: DictionaryDao,
    private val client: () -> DictionaryClient?,
    private val settings: FeatureSettingsStore,
    private val analytics: AnalyticsSink,
    private val clock: () -> Long,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** Ends the lease that [reader] gave out. The pack reader stays open until this runs. */
    private val release: (DictionaryPackReader) -> Unit = {},
) {
    /** The keyboard card uses only this: the pack, never the network or the online cache. */
    suspend fun lookupOffline(word: String): LookupResult = withContext(io) {
        val pack = safely { reader() }
        val (definitions, attribution) = inPack(pack, word)
        val result = LookupResult(
            definitions = definitions,
            offline = true,
            attribution = if (definitions.isEmpty()) null else attribution,
            packMissing = pack == null,
        )
        result.also(::log)
    }

    suspend fun lookup(word: String, allowNetwork: Boolean): LookupResult = withContext(io) { find(word, allowNetwork) }

    private suspend fun find(word: String, allowNetwork: Boolean): LookupResult {
        val pack = safely { reader() }
        val packMissing = pack == null
        val (inPack, attribution) = inPack(pack, word)
        if (inPack.isNotEmpty()) return LookupResult(inPack, true, attribution, packMissing).also(::log)

        val norm = HeadwordNorm.of(word)
        if (norm.isEmpty()) return LookupResult(emptyList(), true, null, packMissing).also(::log)
        val cached = safely { dao.find(LANG, norm) }.orEmpty()
        val fresh = cached.isNotEmpty() && cached.all { clock() - it.fetchedAt < CACHE_TTL_MS }
        if (fresh) {
            return LookupResult(cached.map(::toDefinition), false, onlineAttribution(), packMissing).also(::log)
        }
        if (!allowNetwork || !onlineAllowed()) {
            return LookupResult(emptyList(), true, null, packMissing).also(::log)
        }
        val answer = safely { client()?.lookup(word, LANG) }
        val result = if (answer is DictionaryResult.Found) {
            store(norm, answer)
            LookupResult(
                definitions = answer.entries.map {
                    Definition(it.headword, it.pos, it.definition, it.example, it.synonyms)
                },
                offline = false,
                attribution = onlineAttribution(answer.licenceUrl),
                packMissing = packMissing,
            )
        } else {
            LookupResult(emptyList(), true, null, packMissing)
        }
        return result.also(::log)
    }

    private suspend fun onlineAllowed(): Boolean {
        val current = safely { settings.flow.first() } ?: return false
        return current.onlineDictionary && current.consentAiAt > 0L
    }

    private suspend fun store(norm: String, answer: DictionaryResult.Found) {
        val now = clock()
        safely {
            dao.delete(LANG, norm)
            dao.insert(
                answer.entries.map {
                    DictionaryEntryEntity(
                        lang = LANG,
                        headword = it.headword,
                        headwordNorm = norm,
                        pos = it.pos,
                        definition = it.definition,
                        example = it.example,
                        source = DictionaryEntryEntity.SOURCE_ONLINE,
                        fetchedAt = now,
                        synonyms = if (it.synonyms.isEmpty()) {
                            null
                        } else {
                            JsonArray(it.synonyms.map(::JsonPrimitive)).toString()
                        },
                    )
                },
            )
        }
    }

    private fun toDefinition(e: DictionaryEntryEntity) = Definition(
        headword = e.headword,
        pos = e.pos,
        definition = e.definition,
        example = e.example,
        synonyms = e.synonyms?.let(DictionaryPackReader::parseSynonyms).orEmpty(),
    )

    private fun log(result: LookupResult) {
        runCatching {
            analytics.log(EventCatalog.dictionaryLookup(result.offline, result.definitions.isNotEmpty()))
        }
    }

    /** Runs [block] and drops any failure without logging it, since a message can hold the word. */
    /** Reads the pack, then ends the reader's lease, so a swapped-out pack closes only after this lookup. */
    private fun inPack(pack: DictionaryPackReader?, word: String): Pair<List<Definition>, Attribution?> {
        pack ?: return emptyList<Definition>() to null
        return try {
            (safely { pack.find(word) }.orEmpty()) to pack.attribution
        } finally {
            safely { release(pack) }
        }
    }

    private inline fun <T> safely(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    companion object {
        const val LANG = "en"
        const val CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000

        /** kb-api forwards to the Free Dictionary API, which serves Wiktionary text (OD-11). */
        const val ONLINE_SOURCE = "Free Dictionary API"
        const val ONLINE_ATTRIBUTION = "Wiktionary contributors"
        const val ONLINE_LICENCE_NAME = "CC BY-SA 4.0"

        /** Cached rows keep no licence column, so they show the licence of the online source. */
        const val ONLINE_LICENCE_URL = "https://creativecommons.org/licenses/by-sa/4.0/"

        fun onlineAttribution(licenceUrl: String = ONLINE_LICENCE_URL) =
            Attribution(ONLINE_SOURCE, ONLINE_ATTRIBUTION, ONLINE_LICENCE_NAME, licenceUrl)
    }
}
