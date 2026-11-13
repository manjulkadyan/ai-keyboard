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

/** The online dictionary cache (`dictionary_entry`). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DictionaryDaoTest {
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
                entry("en", "Apple", "a fruit", synonyms = """["pome"]"""),
                entry("en", "apple", "a tech company"),
                entry("en", "pear", "another fruit"),
                entry("de", "apple", "not German"),
            ),
        )
        val rows = dao.find("en", "apple")
        assertEquals(listOf("a fruit", "a tech company"), rows.map { it.definition })
        assertEquals("""["pome"]""", rows[0].synonyms)
        assertEquals(null, rows[1].synonyms)
        assertEquals(DictionaryEntryEntity.SOURCE_ONLINE, rows[0].source)
        assertEquals(emptyList(), dao.find("en", "plum"))
        dao.clearAll()
        assertEquals(emptyList(), dao.find("en", "pear"))
    }

    @Test fun `synonyms keep a JSON array with a comma inside one item`() = runBlocking {
        val dao = db.dictionaryDao()
        val json = """["pome","fruit, edible"]"""
        dao.insert(listOf(entry("en", "apple", "a fruit", synonyms = json), entry("en", "apple", "no synonyms")))
        assertEquals(listOf(json, null), dao.find("en", "apple").map { it.synonyms })
    }
}
