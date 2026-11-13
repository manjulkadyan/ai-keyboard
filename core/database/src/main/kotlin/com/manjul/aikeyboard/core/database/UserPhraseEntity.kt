/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A phrase the user saved: the `user_phrase` table (Sprint 5). [text] and [translation] are user content: they stay
 * on this phone and are never logged.
 */
@Entity(tableName = "user_phrase")
data class UserPhraseEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "translation") val translation: String?,
    @ColumnInfo(name = "target_lang") val targetLang: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
) {
    // Keeps the phrase out of any accidental log or exception message (PC-10).
    override fun toString(): String =
        "UserPhraseEntity(id=$id, targetLang=$targetLang, createdAt=$createdAt, updatedAt=$updatedAt)"

    companion object {
        /** The [PhraseFavouriteEntity.phraseRef] of the user phrase [id]. */
        fun ref(id: Long): String = "u:$id"
    }
}
