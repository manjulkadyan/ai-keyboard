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
    entities = [TranslationHistoryEntity::class, UserWordEntity::class, UserBigramEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        // v2 (Sprint 3): adds the user dictionary tables user_word and user_bigram.
        AutoMigration(from = 1, to = 2),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun translationHistoryDao(): TranslationHistoryDao

    abstract fun userWordDao(): UserWordDao

    companion object {
        const val FILE_NAME = "app_database"

        fun new(context: Context): AppDatabase = Room
            .databaseBuilder(context.applicationContext, AppDatabase::class.java, FILE_NAME)
            // Keeps observers in sync if a second instance (or process) writes history.
            .enableMultiInstanceInvalidation()
            .build()
    }
}
