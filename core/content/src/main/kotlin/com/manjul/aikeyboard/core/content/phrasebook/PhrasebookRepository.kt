/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.phrasebook

import com.manjul.aikeyboard.core.database.PhraseDao
import com.manjul.aikeyboard.core.database.PhraseFavouriteEntity
import com.manjul.aikeyboard.core.database.UserPhraseEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest

/**
 * One phrasebook row. [ref] is `c:<id>` for a catalogue phrase and `u:<id>` for the user's own. [translation] is
 * null when the target is English or the target pack is missing. [machine] marks an ML Kit translation (decision 23).
 */
data class Phrase(
    val ref: String,
    val text: String,
    val translation: String?,
    val machine: Boolean,
    val favourite: Boolean,
    val own: Boolean,
) {
    // A user phrase is user content: it never reaches a log line or an exception message.
    override fun toString(): String =
        "Phrase(ref=$ref, translated=${translation != null}, machine=$machine, favourite=$favourite, own=$own)"
}

/**
 * Translates English phrase text on the device. Answers null when it cannot, for example without the language pack.
 * It never calls the network (FR-22 AC2).
 */
fun interface PhraseTranslator {
    suspend fun translate(text: String, source: String, target: String): String?
}

/**
 * The phrasebook (S-17, PRD FR-22): the bundled catalogue from [reader], the user's own phrases and favourites from
 * [dao] (local only, cleared by Delete my data), and on-device translations from [translator]. It never logs.
 */
class PhrasebookRepository(
    private val reader: PhrasebookReader,
    private val dao: PhraseDao,
    private val translator: PhraseTranslator,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val cache = object : LinkedHashMap<Pair<String, String>, String>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, String>, String>) =
            size > CACHE_SIZE
    }

    /** Drops the cached translations, which hold phrase text. Delete my data calls it. */
    fun clearCache() = synchronized(cache) { cache.clear() }

    /** The virtual "favourites" and "my_phrases" first, then the catalogue categories in `sort` order. */
    fun categories(): List<PhraseCategory> =
        listOf(PhraseCategory(FAVOURITES, -2, virtual = true), PhraseCategory(MY_PHRASES, -1, virtual = true)) +
            reader.categories()

    /**
     * The rows of [categoryKey], translated to [target] (a language tag). It emits again when the user phrases or the
     * favourites change. An unknown key gives an empty list.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun phrases(categoryKey: String, target: String): Flow<List<Phrase>> =
        combine(dao.observePhrases(), dao.observeFavourites()) { own, favourites -> own to favourites }
            .mapLatest { (own, favourites) -> rows(categoryKey, own, favourites).map { translate(it, target) } }

    /** Saves a new user phrase and returns its id. The trimmed text must have 1 to 300 chars. */
    suspend fun addOwn(text: String): Long {
        val clean = validate(text)
        val now = clock()
        return dao.insert(
            UserPhraseEntity(text = clean, translation = null, targetLang = null, createdAt = now, updatedAt = now),
        )
    }

    /** Replaces the text of the user phrase [id]. The trimmed text must have 1 to 300 chars. */
    suspend fun editOwn(id: Long, text: String) {
        val clean = validate(text)
        val old = requireNotNull(dao.phrase(id)) { "no user phrase $id" }
        dao.update(old.copy(text = clean, translation = null, targetLang = null, updatedAt = clock()))
    }

    /** Deletes the user phrase [id] and its favourite. */
    suspend fun deleteOwn(id: Long) = dao.delete(id)

    /** Adds [ref] to the favourites, or removes it. */
    suspend fun toggleFavourite(ref: String) {
        require(ref.startsWith(CATALOG_PREFIX) || ref.startsWith(OWN_PREFIX)) { "bad phrase ref" }
        dao.toggleFavourite(ref, clock())
    }

    private data class Row(
        val ref: String,
        val text: String,
        val own: Boolean,
        val favourite: Boolean,
        val stored: String?,
        val storedLang: String?,
    )

    private fun rows(
        categoryKey: String,
        own: List<UserPhraseEntity>,
        favourites: List<PhraseFavouriteEntity>,
    ): List<Row> {
        val refs = favourites.mapTo(HashSet()) { it.phraseRef }
        fun ownRow(p: UserPhraseEntity): Row {
            val ref = UserPhraseEntity.ref(p.id)
            return Row(
                ref, p.text, own = true, favourite = ref in refs, stored = p.translation, storedLang = p.targetLang,
            )
        }
        fun catalogRow(p: CatalogPhrase): Row {
            val ref = CATALOG_PREFIX + p.id
            return Row(ref, p.text, own = false, favourite = ref in refs, stored = null, storedLang = null)
        }
        return when (categoryKey) {
            MY_PHRASES -> own.map(::ownRow)
            FAVOURITES -> {
                val byRef = own.associateBy { UserPhraseEntity.ref(it.id) }
                favourites.mapNotNull { f ->
                    val ref = f.phraseRef
                    when {
                        ref.startsWith(OWN_PREFIX) -> byRef[ref]?.let(::ownRow)
                        ref.startsWith(CATALOG_PREFIX) ->
                            ref.removePrefix(CATALOG_PREFIX).toLongOrNull()?.let(reader::phrase)?.let(::catalogRow)
                        else -> null
                    }
                }
            }
            else -> reader.phrases(categoryKey).map(::catalogRow)
        }
    }

    private suspend fun translate(row: Row, target: String): Phrase {
        fun phrase(translation: String?, machine: Boolean) =
            Phrase(row.ref, row.text, translation, machine, row.favourite, row.own)
        if (target == SOURCE) return phrase(null, machine = false)
        if (row.stored != null && row.storedLang == target) return phrase(row.stored, machine = false)
        val key = row.text to target
        synchronized(cache) { cache[key] }?.let { return phrase(it, machine = true) }
        val translated = try {
            translator.translate(row.text, SOURCE, target)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        // A null answer is not cached: the pack may arrive later.
        if (translated != null) synchronized(cache) { cache[key] = translated }
        return phrase(translated, machine = translated != null)
    }

    private fun validate(text: String): String {
        val clean = text.trim()
        require(clean.length in 1..MAX_CHARS) { "a phrase needs 1 to $MAX_CHARS characters" }
        return clean
    }

    companion object {
        const val FAVOURITES = "favourites"
        const val MY_PHRASES = "my_phrases"
        const val MAX_CHARS = 300
        const val SOURCE = "en"
        private const val CATALOG_PREFIX = "c:"
        private const val OWN_PREFIX = "u:"
        private const val CACHE_SIZE = 256
    }
}
