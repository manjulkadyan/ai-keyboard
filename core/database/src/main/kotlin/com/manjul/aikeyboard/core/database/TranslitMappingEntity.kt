/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A Roman word the user mapped to a native word, per language (`ur`, `hi`). User content: [toString] hides it. */
@Entity(tableName = "translit_mapping", indices = [Index(value = ["lang", "roman"], unique = true)])
class TranslitMappingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "lang") val lang: String,
    @ColumnInfo(name = "roman") val roman: String,
    @ColumnInfo(name = "native") val native: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
) {
    override fun toString(): String = "TranslitMappingEntity(id=$id, lang=$lang)"
}
