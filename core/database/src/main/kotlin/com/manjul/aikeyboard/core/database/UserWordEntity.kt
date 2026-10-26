/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * A word the user accepted (or removed) in one keyboard language: the `user_word` table (PRD FR-05). [word] is
 * lowercased and is user content: it stays on this phone and is never logged. [blocked] words are never suggested.
 */
@Entity(
    tableName = "user_word",
    primaryKeys = ["locale", "word"],
    indices = [Index(value = ["locale", "last_used_at"])],
)
data class UserWordEntity(
    @ColumnInfo(name = "locale") val locale: String,
    @ColumnInfo(name = "word") val word: String,
    @ColumnInfo(name = "count") val count: Int,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long,
    @ColumnInfo(name = "blocked") val blocked: Boolean,
) {
    // Keeps the word out of any accidental log or exception message (PC-10).
    override fun toString(): String =
        "UserWordEntity(locale=$locale, count=$count, lastUsedAt=$lastUsedAt, blocked=$blocked)"
}
