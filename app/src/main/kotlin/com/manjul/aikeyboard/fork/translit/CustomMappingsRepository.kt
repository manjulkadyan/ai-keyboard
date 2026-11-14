/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translit

import com.manjul.aikeyboard.core.database.TranslitMappingDao
import com.manjul.aikeyboard.core.database.TranslitMappingEntity
import com.manjul.aikeyboard.core.translit.TranslitLang
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The user's Roman to native mappings: Room (`translit_mapping`) plus the engine's in-memory [CustomMappings], kept in
 * step so the next keystroke sees a change. Both words are user content: no error, log or `toString()` holds them.
 * Suspend functions touch Room, so callers run them off the main thread. [canLearn] (learning allowed and not
 * incognito) is read at write time.
 */
class CustomMappingsRepository(
    private val dao: TranslitMappingDao,
    private val graph: () -> TranslitGraph,
    private val canLearn: () -> Boolean,
    private val clock: () -> Long,
) {
    // One writer at a time: a load racing a save or clearAll must not resurrect or drop a mapping.
    private val lock = Mutex()

    fun observe(lang: TranslitLang): Flow<List<TranslitMappingEntity>> = dao.observe(lang.tag)

    /**
     * Saves (or replaces) the mapping of [roman]. Returns false, and writes nothing, when learning is blocked. Throws
     * [IllegalArgumentException] with a fixed message for an invalid word.
     */
    suspend fun save(lang: TranslitLang, roman: String, native: String): Boolean = lock.withLock {
        if (!canLearn()) return@withLock false
        val key = roman.trim().lowercase(Locale.ROOT)
        val value = native.trim()
        require(isValidRoman(key)) { "Invalid Roman word" }
        require(isValidNative(value)) { "Invalid native word" }
        dao.upsert(TranslitMappingEntity(lang = lang.tag, roman = key, native = value, createdAt = clock()))
        graph().mappings.getValue(lang).put(key, value)
        true
    }

    /** Removes one mapping (never blocked: it is the user's own data) and refreshes the in-memory copy. */
    suspend fun delete(id: Long) = lock.withLock {
        dao.delete(id)
        TranslitLang.entries.forEach { reload(it) }
    }

    /** Fills the engine's mappings for [lang] from Room, replacing what it held. */
    suspend fun load(lang: TranslitLang) = lock.withLock { reload(lang) }

    private suspend fun reload(lang: TranslitLang) {
        val rows = dao.all(lang.tag)
        graph().mappings.getValue(lang).replaceAll(rows.associate { it.roman to it.native })
    }

    /** Clear data: Room, the in-memory mappings and the in-memory boosts. Never blocked. */
    suspend fun clearAll() = lock.withLock {
        try {
            dao.clearAll()
        } finally {
            val g = graph()
            g.mappings.values.forEach { it.replaceAll(emptyMap()) }
            g.clearBoosts()
        }
    }

    companion object {
        const val MAX_NATIVE = 40

        fun isValidRoman(roman: String) = roman.isNotEmpty() && roman.all { it in 'a'..'z' }

        fun isValidNative(native: String) = native.isNotEmpty() && native.length <= MAX_NATIVE
    }
}
