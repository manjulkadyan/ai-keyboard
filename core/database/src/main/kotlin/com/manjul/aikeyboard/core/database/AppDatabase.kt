/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The fork's Room database (`databases/app_database`). Version bumps need a `Migration` and the exported schema
 * in `core/database/schemas/`; there is no destructive fallback, a missing migration must fail loudly instead
 * of wiping the user's history.
 */
@Database(
    entities = [
        TranslationHistoryEntity::class,
        UserWordEntity::class,
        UserBigramEntity::class,
        DictionaryEntryEntity::class,
        StickerPackEntity::class,
        StickerEntity::class,
        UserPhraseEntity::class,
        PhraseFavouriteEntity::class,
        TranslitMappingEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [
        // v2 (Sprint 3): adds the user dictionary tables user_word and user_bigram.
        AutoMigration(from = 1, to = 2),
        // v3 (Sprint 5): adds dictionary_entry, sticker_pack, sticker, user_phrase and phrase_favourite.
        AutoMigration(from = 2, to = 3),
        // v4 (Sprint 6): adds translit_mapping (Roman typing mappings).
        AutoMigration(from = 3, to = 4),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun translationHistoryDao(): TranslationHistoryDao

    abstract fun userWordDao(): UserWordDao

    abstract fun dictionaryDao(): DictionaryDao

    abstract fun stickerDao(): StickerDao

    abstract fun phraseDao(): PhraseDao

    abstract fun translitMappingDao(): TranslitMappingDao

    companion object {
        const val FILE_NAME = "app_database"
        const val VERSION = 4

        fun new(context: Context): AppDatabase = Room
            .databaseBuilder(context.applicationContext, AppDatabase::class.java, FILE_NAME)
            // Keeps observers in sync if a second instance (or process) writes history.
            .enableMultiInstanceInvalidation()
            .build()
    }
}
