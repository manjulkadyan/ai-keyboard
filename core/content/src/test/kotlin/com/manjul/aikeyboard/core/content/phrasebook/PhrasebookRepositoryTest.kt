/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.phrasebook

import android.app.Application
import androidx.room.Room
import com.manjul.aikeyboard.core.database.AppDatabase
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** FR-22: the catalogue, the user's own phrases (AC3), favourites and on-device machine translation (AC2). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhrasebookRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val app: Application = RuntimeEnvironment.getApplication()
    private val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
    private val asset = File(System.getProperty("content.phrasebookDb")!!)
    private val reader by lazy { PhrasebookReader.open(tmp.newFolder("no_backup"), 1) { asset.inputStream() }!! }
    private val calls = mutableListOf<Triple<String, String, String>>()
    private var now = 1_000L

    private fun repository(translate: (String, String) -> String? = { text, target -> "[$target] $text" }) =
        PhrasebookRepository(reader, db.phraseDao(), { text, source, target ->
            calls += Triple(text, source, target)
            translate(text, target)
        }, clock = { now++ })

    @After
    fun close() {
        reader.close()
        db.close()
    }

    @Test
    fun categoriesAreTheVirtualOnesThenTheCatalogue() {
        val keys = repository().categories().map { it.key }
        assertEquals(listOf(PhrasebookRepository.FAVOURITES, PhrasebookRepository.MY_PHRASES), keys.take(2))
        assertEquals(14, keys.size)
        assertEquals("greetings", keys[2])
        assertTrue(repository().categories().take(2).all { it.virtual })
    }

    @Test
    fun addEditAndDeleteAUserPhrase() = runBlocking {
        val repo = repository()
        val id = repo.addOwn("  Where is my bag?  ")
        val added = repo.phrases(PhrasebookRepository.MY_PHRASES, "es").first().single()
        assertEquals("u:$id", added.ref)
        assertEquals("Where is my bag?", added.text)
        assertTrue(added.own)

        repo.editOwn(id, "Where is my hat?")
        assertEquals(
            listOf("Where is my hat?"),
            repo.phrases(PhrasebookRepository.MY_PHRASES, "es").first().map { it.text },
        )

        repo.toggleFavourite("u:$id")
        repo.deleteOwn(id)
        assertTrue(repo.phrases(PhrasebookRepository.MY_PHRASES, "es").first().isEmpty())
        assertTrue(repo.phrases(PhrasebookRepository.FAVOURITES, "es").first().isEmpty())
    }

    @Test
    fun anEmptyOrTooLongPhraseIsRejected() = runBlocking {
        val repo = repository()
        for (bad in listOf("", "   ", "a".repeat(301))) {
            val e = assertFailsWith<IllegalArgumentException> { repo.addOwn(bad) }
            assertFalse(e.message.orEmpty().contains("aaa"), "the message never carries the text")
        }
        val id = repo.addOwn("a".repeat(300))
        assertFailsWith<IllegalArgumentException> { repo.editOwn(id, "") }
        assertFailsWith<IllegalArgumentException> { repo.editOwn(id, "b".repeat(301)) }
        val texts = repo.phrases(PhrasebookRepository.MY_PHRASES, "es").first().map { it.text }
        assertEquals(listOf("a".repeat(300)), texts)
    }

    @Test
    fun aFavouriteShowsInFavourites() = runBlocking {
        val repo = repository()
        val id = repo.addOwn("My hotel is the blue one.")
        repo.toggleFavourite("c:1")
        repo.toggleFavourite("u:$id")

        val favourites = repo.phrases(PhrasebookRepository.FAVOURITES, "es").first()
        assertEquals(setOf("c:1", "u:$id"), favourites.map { it.ref }.toSet())
        assertTrue(favourites.all { it.favourite })
        val greetings = repo.phrases("greetings", "es").first()
        assertEquals(listOf(true, false, false), greetings.map { it.favourite })

        repo.toggleFavourite("c:1")
        assertEquals(listOf("u:$id"), repo.phrases(PhrasebookRepository.FAVOURITES, "es").first().map { it.ref })
    }

    @Test
    fun aMissingTranslationIsMachineTranslated() = runBlocking {
        val repo = repository()
        repo.addOwn("See you tomorrow.")
        val greetings = repo.phrases("greetings", "es").first()
        assertEquals("Hello, nice to meet you.", greetings.first().text)
        assertEquals("[es] Hello, nice to meet you.", greetings.first().translation)
        assertTrue(greetings.all { it.machine && !it.own })
        val own = repo.phrases(PhrasebookRepository.MY_PHRASES, "fr").first().single()
        assertEquals("[fr] See you tomorrow.", own.translation)
        assertTrue(own.machine)
        assertTrue(calls.all { it.second == "en" })
    }

    @Test
    fun aTranslatorWithoutThePackGivesNoTranslation() = runBlocking {
        // The ML Kit adapter answers null without the pack; the repository has no other way to translate.
        val repo = repository { _, _ -> null }
        val greetings = repo.phrases("greetings", "ur").first()
        assertEquals(3, greetings.size)
        assertTrue(greetings.all { it.translation == null && !it.machine })
        assertEquals(3, calls.size)
    }

    @Test
    fun englishNeedsNoTranslation() = runBlocking {
        val greetings = repository().phrases("greetings", "en").first()
        assertTrue(greetings.all { it.translation == null && !it.machine })
        assertTrue(calls.isEmpty())
    }

    @Test
    fun phraseTextStaysOutOfToString() = runBlocking {
        val phrase = repository().phrases("greetings", "es").first().first()
        assertFalse(phrase.toString().contains("Hello"))
        assertNull(repository().phrases("unknown", "es").first().firstOrNull())
    }
}
