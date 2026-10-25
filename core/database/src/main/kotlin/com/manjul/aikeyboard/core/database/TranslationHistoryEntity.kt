/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One finished Translator translation (TRD §3.1 `translation_history`, PRD FR-12). The texts are user content:
 * they stay in this table and are never logged or sent anywhere. Languages are BCP-47 tags.
 */
@Entity(
    tableName = "translation_history",
    indices = [Index(value = ["created_at"]), Index(value = ["is_favorite"])],
)
data class TranslationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "source_text") val sourceText: String,
    @ColumnInfo(name = "target_text") val targetText: String,
    @ColumnInfo(name = "source_lang") val sourceLang: String,
    @ColumnInfo(name = "target_lang") val targetLang: String,
    /** `mlkit` or `cloud`. */
    @ColumnInfo(name = "engine") val engine: String,
    /** `text`, `voice` or `process_text`. */
    @ColumnInfo(name = "origin") val origin: String,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
) {
    // Keeps the texts out of any accidental log or exception message (PC-10).
    override fun toString(): String =
        "TranslationHistoryEntity(id=$id, $sourceLang->$targetLang, engine=$engine, origin=$origin, " +
            "isFavorite=$isFavorite, createdAt=$createdAt)"
}
