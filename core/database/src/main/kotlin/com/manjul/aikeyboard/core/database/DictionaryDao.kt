/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** The online dictionary cache (`dictionary_entry`). */
@Dao
interface DictionaryDao {
    @Insert
    suspend fun insert(entries: List<DictionaryEntryEntity>)

    /** The cached definitions of [headwordNorm] in [lang], in the order they were stored. */
    @Query("SELECT * FROM dictionary_entry WHERE lang = :lang AND headword_norm = :headwordNorm ORDER BY id")
    suspend fun find(lang: String, headwordNorm: String): List<DictionaryEntryEntity>

    @Query("DELETE FROM dictionary_entry")
    suspend fun clearAll()
}
