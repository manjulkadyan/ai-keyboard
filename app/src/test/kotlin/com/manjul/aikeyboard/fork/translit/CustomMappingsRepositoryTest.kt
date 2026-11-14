/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translit

import androidx.room.Room
import com.manjul.aikeyboard.core.database.AppDatabase
import com.manjul.aikeyboard.core.translit.TranslitLang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CustomMappingsRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private var graph = TranslitGraph({ error("no assets") }, Dispatchers.Unconfined)
    private var canLearn = true
    private val repo = CustomMappingsRepository(db.translitMappingDao(), { graph }, { canLearn }, { 7L })

    @After fun tearDown() = db.close()

    @Test fun `save with canLearn false returns false and writes nothing`() = runBlocking {
        canLearn = false
        assertFalse(repo.save(TranslitLang.UR, "kya", "کیا"))
        assertEquals(emptyList(), db.translitMappingDao().all("ur"))
        assertNull(graph.mappings.getValue(TranslitLang.UR).get("kya"))
    }

    @Test fun `save normalises, stores and fills the engine mappings at once`() = runBlocking {
        assertTrue(repo.save(TranslitLang.UR, "  KyA ", " کیا "))
        val row = db.translitMappingDao().all("ur").single()
        assertEquals("kya", row.roman)
        assertEquals("کیا", row.native)
        assertEquals(7L, row.createdAt)
        assertEquals("کیا", graph.mappings.getValue(TranslitLang.UR).get("kya"))
        assertNull(graph.mappings.getValue(TranslitLang.HI).get("kya"))
    }

    @Test fun `load restores the mappings after a new graph`() = runBlocking {
        repo.save(TranslitLang.UR, "kya", "کیا")
        repo.save(TranslitLang.HI, "kya", "क्या")
        graph = TranslitGraph({ error("no assets") }, Dispatchers.Unconfined)
        assertNull(graph.mappings.getValue(TranslitLang.UR).get("kya"))
        repo.load(TranslitLang.UR)
        assertEquals("کیا", graph.mappings.getValue(TranslitLang.UR).get("kya"))
        assertNull(graph.mappings.getValue(TranslitLang.HI).get("kya"))
    }

    @Test fun `a bad word is rejected without its value in the message`() = runBlocking {
        for ((roman, native) in listOf(
            "" to "کیا", "   " to "کیا", "kyā9" to "کیا", "ky a" to "کیا", "kya" to "", "kya" to "ک".repeat(41),
        )) {
            val e = assertFailsWith<IllegalArgumentException> { repo.save(TranslitLang.UR, roman, native) }
            assertFalse(roman.isNotBlank() && e.message.orEmpty().contains(roman.trim()))
            assertFalse(e.message.orEmpty().contains("کیا"))
        }
        assertTrue(repo.save(TranslitLang.UR, "kya", "ک".repeat(40)))
        assertEquals(1, db.translitMappingDao().all("ur").size)
    }

    @Test fun `delete removes the row and the in-memory mapping`() = runBlocking {
        repo.save(TranslitLang.UR, "kya", "کیا")
        repo.delete(db.translitMappingDao().all("ur").single().id)
        assertEquals(emptyList(), db.translitMappingDao().all("ur"))
        assertNull(graph.mappings.getValue(TranslitLang.UR).get("kya"))
    }

    @Test fun `clearAll empties Room, the mappings and the boosts even in incognito`() = runBlocking {
        repo.save(TranslitLang.UR, "kya", "کیا")
        graph.boost(TranslitLang.UR, "kya", "کیا")
        canLearn = false
        repo.clearAll()
        assertEquals(emptyList(), db.translitMappingDao().all("ur"))
        assertNull(graph.mappings.getValue(TranslitLang.UR).get("kya"))
        assertEquals(0, graph.boostCount())
    }

    @Test fun `the save handler re-checks learning at tap time and swallows a failure`() = runBlocking {
        val ctx = com.manjul.aikeyboard.fork.suggest.SuggestContext()
        var incognito = false
        val provider = TranslitSuggestionProvider({ graph }, ctx, { incognito })
        ctx.update(
            android.view.inputmethod.EditorInfo().apply { inputType = android.text.InputType.TYPE_CLASS_TEXT },
            com.manjul.aikeyboard.ime.policy.PolicyDecision.Unrestricted,
        )
        fun candidate(latin: Boolean = false) = TranslitCandidate(
            "کیا", 1.0, false, provider, "kya", TranslitLang.UR, com.manjul.aikeyboard.core.translit.Source.RULES, latin,
        )
        val handler = SaveMappingHandler({ repo }, kotlinx.coroutines.GlobalScope, Dispatchers.Unconfined)

        incognito = true // turned on after the card opened
        handler(candidate())
        assertEquals(emptyList(), db.translitMappingDao().all("ur"))

        incognito = false
        handler(candidate(latin = true))
        assertEquals(emptyList(), db.translitMappingDao().all("ur"))
        handler(candidate())
        assertEquals("کیا", db.translitMappingDao().all("ur").single().native)

        db.close() // a failing write must not throw out of the handler
        handler(candidate())
    }
}
