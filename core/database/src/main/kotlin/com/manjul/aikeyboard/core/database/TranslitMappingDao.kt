/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** User Roman to native mappings (`translit_mapping`). */
@Dao
interface TranslitMappingDao {
    @Query("SELECT * FROM translit_mapping WHERE lang = :lang ORDER BY roman")
    fun observe(lang: String): Flow<List<TranslitMappingEntity>>

    @Query("SELECT * FROM translit_mapping WHERE lang = :lang")
    suspend fun all(lang: String): List<TranslitMappingEntity>

    /**
     * Same `(lang, roman)` replaces the native word. Not `@Upsert`: it updates by primary key, so a new entity
     * (id 0) that hits the unique index would not replace anything. REPLACE resolves the unique conflict.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TranslitMappingEntity)

    @Query("DELETE FROM translit_mapping WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM translit_mapping")
    suspend fun clearAll()
}
