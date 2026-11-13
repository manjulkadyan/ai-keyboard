/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.database

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Sprint 5 tables: the online dictionary cache, sticker packs and stickers, user phrases and favourites. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContentDaoTest {
    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @After fun tearDown() = db.close()

    private fun entry(lang: String, headword: String, definition: String, synonyms: String? = null) =
        DictionaryEntryEntity(
            lang = lang,
            headword = headword,
            headwordNorm = headword.lowercase(),
            pos = "noun",
            definition = definition,
            example = null,
            source = DictionaryEntryEntity.SOURCE_ONLINE,
            fetchedAt = 7L,
            synonyms = synonyms,
        )

    @Test fun `the dictionary cache returns rows by lang and headword_norm`() = runBlocking {
        val dao = db.dictionaryDao()
        dao.insert(
            listOf(
                entry("en", "Apple", "a fruit", synonyms = "pome"),
                entry("en", "apple", "a tech company"),
                entry("en", "pear", "another fruit"),
                entry("de", "apple", "not German"),
            ),
        )
        val rows = dao.find("en", "apple")
        assertEquals(listOf("a fruit", "a tech company"), rows.map { it.definition })
        assertEquals("pome", rows[0].synonyms)
        assertEquals(DictionaryEntryEntity.SOURCE_ONLINE, rows[0].source)
        assertEquals(emptyList(), dao.find("en", "plum"))
        dao.clearAll()
        assertEquals(emptyList(), dao.find("en", "pear"))
    }

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

    private fun phrase(text: String) =
        UserPhraseEntity(text = text, translation = null, targetLang = null, createdAt = 1, updatedAt = 1)

    @Test fun `a favourite toggles`() = runBlocking {
        val dao = db.phraseDao()
        assertFalse(dao.isFavourite("c:greet-1"))
        assertTrue(dao.toggleFavourite("c:greet-1", now = 5))
        assertTrue(dao.isFavourite("c:greet-1"))
        assertEquals(listOf("c:greet-1"), dao.observeFavourites().first().map { it.phraseRef })
        assertFalse(dao.toggleFavourite("c:greet-1", now = 6))
        assertFalse(dao.isFavourite("c:greet-1"))
    }

    @Test fun `user phrases are stored, updated and cleared with their favourites`() = runBlocking {
        val dao = db.phraseDao()
        val id = dao.insert(phrase("On my way"))
        dao.update(dao.phrase(id)!!.copy(translation = "En camino", targetLang = "es", updatedAt = 2))
        val saved = dao.observePhrases().first().single()
        assertEquals("En camino", saved.translation)
        assertEquals(2L, saved.updatedAt)
        assertFalse(saved.toString().contains("On my way"), "toString must hide user text")
        dao.toggleFavourite(UserPhraseEntity.ref(id), now = 3)
        assertEquals("u:$id", UserPhraseEntity.ref(id))

        dao.clearAll()
        assertEquals(emptyList(), dao.observePhrases().first())
        assertFalse(dao.isFavourite(UserPhraseEntity.ref(id)))
    }

    @Test fun `deleting a user phrase drops its favourite`() = runBlocking {
        val dao = db.phraseDao()
        val id = dao.insert(phrase("x"))
        dao.toggleFavourite(UserPhraseEntity.ref(id), now = 2)
        dao.delete(id)
        assertEquals(null, dao.phrase(id))
        assertFalse(dao.isFavourite(UserPhraseEntity.ref(id)))
    }
}
