/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One sticker of a [StickerPackEntity]: the `sticker` table. Deleting the pack deletes its stickers. */
@Entity(
    tableName = "sticker",
    foreignKeys = [
        ForeignKey(
            entity = StickerPackEntity::class,
            parentColumns = ["id"],
            childColumns = ["pack_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["pack_id"])],
)
data class StickerEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "pack_id") val packId: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "mime") val mime: String,
    /** Space separated search keywords from the pack. */
    @ColumnInfo(name = "keywords") val keywords: String,
)
