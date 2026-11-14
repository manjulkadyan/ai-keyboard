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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TranslitMappingDaoTest {
    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val dao = db.translitMappingDao()

    @After fun tearDown() = db.close()

    private fun row(lang: String, roman: String, native: String) =
        TranslitMappingEntity(lang = lang, roman = roman, native = native, createdAt = 1)

    @Test fun `upsert on the same lang and roman replaces the native word`() = runBlocking {
        dao.upsert(row("ur", "kya", "کیا"))
        dao.upsert(row("ur", "kya", "کیا؟"))
        dao.upsert(row("hi", "kya", "क्या"))
        assertEquals(listOf("کیا؟"), dao.all("ur").map { it.native })
        assertEquals(listOf("क्या"), dao.all("hi").map { it.native })
    }

    @Test fun `delete removes one row and clearAll removes every row`() = runBlocking {
        dao.upsert(row("ur", "a", "ا"))
        dao.upsert(row("ur", "b", "ب"))
        val a = dao.all("ur").single { it.roman == "a" }
        dao.delete(a.id)
        assertEquals(listOf("b"), dao.all("ur").map { it.roman })
        dao.upsert(row("hi", "c", "क"))
        dao.clearAll()
        assertEquals(emptyList(), dao.all("ur"))
        assertEquals(emptyList(), dao.all("hi"))
    }

    @Test fun `observe emits in roman order`() = runBlocking {
        dao.upsert(row("ur", "zaban", "زبان"))
        dao.upsert(row("ur", "aam", "عام"))
        dao.upsert(row("ur", "kya", "کیا"))
        assertEquals(listOf("aam", "kya", "zaban"), dao.observe("ur").first().map { it.roman })
    }

    @Test fun `toString holds no text`() {
        val s = row("ur", "kyaaa", "کیا").toString()
        assertFalse(s.contains("kyaaa") || s.contains("کیا"))
        assertEquals("TranslitMappingEntity(id=0, lang=ur)", s)
    }
}
