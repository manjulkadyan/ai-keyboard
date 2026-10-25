/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.database

import androidx.room.Room
import java.io.File
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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TranslationHistoryRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private var now = 1_000L
    private val repo = TranslationHistoryRepository(db.translationHistoryDao(), clock = { now++ }, maxRows = 500)

    @After fun tearDown() = db.close()

    private fun entry(source: String, target: String = "[$source]") =
        TranslationHistoryEntry(source, target, "en", "ur", "mlkit", "text")

    private suspend fun all(query: String? = null, favoritesOnly: Boolean = false) =
        repo.page(query, favoritesOnly).first()

    @Test fun `insert lists newest first`() = runBlocking {
        repo.upsertSession(null, entry("hello"))
        repo.upsertSession(null, entry("world"))
        assertEquals(listOf("world", "hello"), all().map { it.sourceText })
        val row = all().first()
        assertEquals("en", row.sourceLang)
        assertEquals("ur", row.targetLang)
        assertEquals("mlkit", row.engine)
        assertEquals("text", row.origin)
        assertFalse(row.isFavorite)
    }

    @Test fun `search matches either text and treats wildcards literally`() = runBlocking {
        repo.upsertSession(null, entry("good morning", "subah bakhair"))
        repo.upsertSession(null, entry("thank you", "shukriya"))
        repo.upsertSession(null, entry("100% sure", "pakka"))
        assertEquals(listOf("good morning"), all("MORNING").map { it.sourceText })
        assertEquals(listOf("thank you"), all("shuk").map { it.sourceText })
        assertEquals(listOf("100% sure"), all("0%").map { it.sourceText })
        assertTrue(all("_").isEmpty())
        assertEquals(3, all("  ").size)
    }

    @Test fun `favourites filter and toggle`() = runBlocking {
        val a = repo.upsertSession(null, entry("a"))
        repo.upsertSession(null, entry("b"))
        repo.setFavorite(a, true)
        assertEquals(listOf("a"), all(favoritesOnly = true).map { it.sourceText })
        repo.setFavorite(a, false)
        assertTrue(all(favoritesOnly = true).isEmpty())
    }

    @Test fun `delete removes one row`() = runBlocking {
        val a = repo.upsertSession(null, entry("a"))
        repo.upsertSession(null, entry("b"))
        repo.delete(a)
        assertEquals(listOf("b"), all().map { it.sourceText })
    }

    @Test fun `clear all keeps favourites only when asked`() = runBlocking {
        val a = repo.upsertSession(null, entry("a"))
        repo.upsertSession(null, entry("b"))
        repo.setFavorite(a, true)
        repo.clearAll(keepFavorites = true)
        assertEquals(listOf("a"), all().map { it.sourceText })
        repo.clearAll(keepFavorites = false)
        assertTrue(all().isEmpty())
    }

    @Test fun `501st insert trims the oldest non-favourite`() = runBlocking {
        val first = repo.upsertSession(null, entry("s0"))
        repo.setFavorite(first, true)
        for (i in 1 until 500) repo.upsertSession(null, entry("s$i"))
        assertEquals(500, all().size)
        repo.upsertSession(null, entry("s500"))
        val texts = all().map { it.sourceText }
        assertEquals(500, texts.size)
        assertTrue("s0" in texts, "favourites are never trimmed")
        assertFalse("s1" in texts, "the oldest non-favourite goes first")
        assertTrue("s2" in texts && "s500" in texts)
    }

    @Test fun `upsertSession updates the same row`() = runBlocking {
        val id = repo.upsertSession(null, entry("he", "wo"))
        repo.setFavorite(id, true)
        val again = repo.upsertSession(id, entry("hello", "world"))
        assertEquals(id, again)
        val rows = all()
        assertEquals(1, rows.size)
        assertEquals("hello", rows.single().sourceText)
        assertEquals("world", rows.single().targetText)
        assertTrue(rows.single().isFavorite, "an update keeps the favourite flag")
    }

    @Test fun `upsertSession inserts again when the session row is gone`() = runBlocking {
        val id = repo.upsertSession(null, entry("a"))
        repo.delete(id)
        val next = repo.upsertSession(id, entry("b"))
        assertNotEquals(id, next)
        assertEquals(listOf("b"), all().map { it.sourceText })
    }

    @Test fun `entities and entries keep the texts out of toString`() {
        val e = TranslationHistoryEntity(sourceText = "secret", targetText = "hidden", sourceLang = "en",
            targetLang = "ur", engine = "mlkit", origin = "text", createdAt = 1)
        assertFalse(e.toString().contains("secret") || e.toString().contains("hidden"))
        assertFalse(entry("secret", "hidden").toString().let { it.contains("secret") || it.contains("hidden") })
    }

    @Test fun `schema for version 1 is exported`() {
        val schema = File("schemas/${AppDatabase::class.java.name}/1.json")
        assertTrue(schema.isFile, "missing ${schema.path}; run :core:database:kspDebugKotlin")
        val json = schema.readText()
        assertTrue(json.contains("\"translation_history\""))
        for (column in listOf("source_text", "target_text", "source_lang", "target_lang", "engine", "origin",
            "is_favorite", "created_at")) {
            assertTrue(json.contains("\"$column\""), "schema lacks $column")
        }
    }
}
