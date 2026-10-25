/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.database

import kotlinx.coroutines.flow.Flow

/** What a Translator edit session saves; the repository owns the row id, favourite flag and timestamp. */
data class TranslationHistoryEntry(
    val sourceText: String,
    val targetText: String,
    val sourceLang: String,
    val targetLang: String,
    val engine: String,
    val origin: String,
) {
    override fun toString(): String = "TranslationHistoryEntry($sourceLang->$targetLang, engine=$engine, origin=$origin)"
}

/**
 * Translator history (PRD FR-12). One row per edit session (plan decision 6): the screen keeps the id returned by
 * [upsertSession] and passes it back while the user keeps typing; it passes `null` to start a new row after a
 * clear, swap, pair change or leaving the screen. Inserting trims the table to [maxRows], oldest non-favourites
 * first; favourites are never trimmed.
 */
class TranslationHistoryRepository(
    private val dao: TranslationHistoryDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxRows: Int = 500,
) {
    /** Newest first; [query] matches either text (case-insensitive for ASCII, `%` and `_` taken literally). */
    fun page(query: String?, favoritesOnly: Boolean): Flow<List<TranslationHistoryEntity>> {
        val pattern = query?.trim()?.takeIf { it.isNotEmpty() }?.let { "%${escapeLike(it)}%" }
        return dao.observe(pattern, favoritesOnly, maxRows)
    }

    /**
     * Updates the session row [sessionId] with [entry] (keeping its favourite flag) and returns its id, or inserts
     * a new row when [sessionId] is `null` or that row is gone (deleted or trimmed meanwhile).
     */
    suspend fun upsertSession(sessionId: Long?, entry: TranslationHistoryEntry): Long {
        val now = clock()
        val existing = sessionId?.let { dao.byId(it) }
        if (existing != null) {
            dao.update(
                existing.copy(
                    sourceText = entry.sourceText,
                    targetText = entry.targetText,
                    sourceLang = entry.sourceLang,
                    targetLang = entry.targetLang,
                    engine = entry.engine,
                    origin = entry.origin,
                    createdAt = now,
                ),
            )
            return existing.id
        }
        val row = TranslationHistoryEntity(
            sourceText = entry.sourceText,
            targetText = entry.targetText,
            sourceLang = entry.sourceLang,
            targetLang = entry.targetLang,
            engine = entry.engine,
            origin = entry.origin,
            createdAt = now,
        )
        return dao.insertAndTrim(row, maxRows)
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun clearAll(keepFavorites: Boolean) {
        if (keepFavorites) dao.deleteNonFavorites() else dao.deleteAll()
    }

    private fun escapeLike(s: String): String = buildString(s.length) {
        for (c in s) {
            if (c == '\\' || c == '%' || c == '_') append('\\')
            append(c)
        }
    }
}
