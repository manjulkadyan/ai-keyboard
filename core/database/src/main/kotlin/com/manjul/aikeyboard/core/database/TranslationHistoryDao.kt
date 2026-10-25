/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TranslationHistoryDao {
    /**
     * Newest first. [pattern] is a `LIKE` pattern with `\` as the escape character, matched against both
     * texts; `null` lists everything.
     */
    @Query(
        """
        SELECT * FROM translation_history
        WHERE (:favoritesOnly = 0 OR is_favorite = 1)
          AND (:pattern IS NULL OR source_text LIKE :pattern ESCAPE '\' OR target_text LIKE :pattern ESCAPE '\')
        ORDER BY created_at DESC, id DESC
        LIMIT :limit
        """,
    )
    abstract fun observe(pattern: String?, favoritesOnly: Boolean, limit: Int): Flow<List<TranslationHistoryEntity>>

    @Query("SELECT * FROM translation_history WHERE id = :id")
    abstract suspend fun byId(id: Long): TranslationHistoryEntity?

    @Insert
    abstract suspend fun insert(entity: TranslationHistoryEntity): Long

    @Update
    abstract suspend fun update(entity: TranslationHistoryEntity): Int

    @Query("UPDATE translation_history SET is_favorite = :favorite WHERE id = :id")
    abstract suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("DELETE FROM translation_history WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("DELETE FROM translation_history")
    abstract suspend fun deleteAll()

    @Query("DELETE FROM translation_history WHERE is_favorite = 0")
    abstract suspend fun deleteNonFavorites()

    @Query("SELECT COUNT(*) FROM translation_history")
    abstract suspend fun count(): Int

    /** Deletes the [n] oldest non-favourite rows. */
    @Query(
        """
        DELETE FROM translation_history WHERE id IN (
            SELECT id FROM translation_history WHERE is_favorite = 0 ORDER BY created_at ASC, id ASC LIMIT :n
        )
        """,
    )
    abstract suspend fun deleteOldestNonFavorites(n: Int)

    /** Inserts [entity] and trims the table to [maxRows], dropping the oldest non-favourites first. */
    @Transaction
    open suspend fun insertAndTrim(entity: TranslationHistoryEntity, maxRows: Int): Long {
        val id = insert(entity)
        val excess = count() - maxRows
        if (excess > 0) deleteOldestNonFavorites(excess)
        return id
    }
}
