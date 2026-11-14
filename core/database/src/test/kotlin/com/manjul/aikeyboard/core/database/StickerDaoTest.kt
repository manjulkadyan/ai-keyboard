/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.database

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** Sticker packs and stickers (`sticker_pack`, `sticker`). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StickerDaoTest {
    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @After fun tearDown() = db.close()

    private fun pack(id: String, premium: Boolean) = StickerPackEntity(
        id = id,
        title = "Pack $id",
        version = 1,
        isPremium = premium,
        manifestUrl = "manifest.json",
        downloaded = true,
        updatedAt = 1,
    )

    @Test fun `deleting a sticker pack cascades to its stickers`() = runBlocking {
        val dao = db.stickerDao()
        dao.upsertPack(pack("p1", premium = false))
        dao.upsertPack(pack("p2", premium = true))
        dao.upsertStickers(
            listOf(
                StickerEntity("p1/01", "p1", "/f/01.png", "image/png", "cat happy"),
                StickerEntity("p1/02", "p1", "/f/02.png", "image/png", "dog"),
                StickerEntity("p2/01", "p2", "/g/01.png", "image/png", "sun"),
            ),
        )
        assertEquals(2, dao.stickers("p1").size)
        dao.deletePack("p1")
        assertEquals(emptyList(), dao.stickers("p1"))
        assertEquals(listOf("p2/01"), dao.stickers("p2").map { it.id })
        assertEquals(listOf("p2"), dao.packs().map { it.id })
        dao.clearAll()
        assertEquals(emptyList(), dao.packs())
        assertEquals(emptyList(), dao.stickers("p2"))
    }
}
