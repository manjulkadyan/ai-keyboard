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

/** User phrases and favourites (`user_phrase`, `phrase_favourite`). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhraseDaoTest {
    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @After fun tearDown() = db.close()

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
