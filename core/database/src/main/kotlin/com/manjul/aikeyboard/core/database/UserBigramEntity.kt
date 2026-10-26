/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * How often the user accepted [word] right after [prev] in one language: the `user_bigram` table (plan decision 3,
 * bigrams are learned only). Both words are lowercased user content and are never logged.
 */
@Entity(tableName = "user_bigram", primaryKeys = ["locale", "prev", "word"])
data class UserBigramEntity(
    @ColumnInfo(name = "locale") val locale: String,
    @ColumnInfo(name = "prev") val prev: String,
    @ColumnInfo(name = "word") val word: String,
    @ColumnInfo(name = "count") val count: Int,
) {
    // Keeps the words out of any accidental log or exception message (PC-10).
    override fun toString(): String = "UserBigramEntity(locale=$locale, count=$count)"
}
