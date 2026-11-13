/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A favourite phrase: the `phrase_favourite` table (Sprint 5). [phraseRef] is `c:<id>` for a catalogue phrase or
 * `u:<id>` for a [UserPhraseEntity].
 */
@Entity(tableName = "phrase_favourite")
data class PhraseFavouriteEntity(
    @PrimaryKey @ColumnInfo(name = "phrase_ref") val phraseRef: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
