/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A sticker pack known from the content manifest: the `sticker_pack` table (Sprint 5). Holds no user content. */
@Entity(tableName = "sticker_pack")
data class StickerPackEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "version") val version: Int,
    @ColumnInfo(name = "is_premium") val isPremium: Boolean,
    @ColumnInfo(name = "manifest_url") val manifestUrl: String,
    @ColumnInfo(name = "downloaded") val downloaded: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
