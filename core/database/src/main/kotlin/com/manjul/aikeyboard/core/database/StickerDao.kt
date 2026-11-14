/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

/** Sticker packs and their stickers. */
@Dao
abstract class StickerDao {
    @Upsert
    abstract suspend fun upsertPack(pack: StickerPackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertStickers(stickers: List<StickerEntity>)

    /** Deletes the pack; the foreign key deletes its stickers. */
    @Query("DELETE FROM sticker_pack WHERE id = :packId")
    abstract suspend fun deletePack(packId: String)

    @Query("SELECT * FROM sticker_pack ORDER BY id")
    abstract suspend fun packs(): List<StickerPackEntity>

    @Query("SELECT * FROM sticker WHERE pack_id = :packId ORDER BY id")
    abstract suspend fun stickers(packId: String): List<StickerEntity>

    @Query("DELETE FROM sticker")
    protected abstract suspend fun deleteAllStickers()

    @Query("DELETE FROM sticker_pack")
    protected abstract suspend fun deleteAllPacks()

    @Transaction
    open suspend fun clearAll() {
        deleteAllStickers()
        deleteAllPacks()
    }
}
