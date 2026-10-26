/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.database

import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserWordRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var now = 1_000L
    private val repo = newRepo()

    private fun newRepo() = UserWordRepository(db.userWordDao(), clock = { now++ }, scope = scope)

    @After fun tearDown() {
        scope.cancel()
        db.close()
    }

    @Test fun `recorded word gets a boost at once and after a reload`() = runBlocking {
        repo.load("en")
        assertEquals(0, repo.boost("en", "hello"))
        repo.recordAccepted("en", "Hello", prev = null)
        assertEquals(6, repo.boost("en", "hello"))
        repo.recordAccepted("en", "hello", prev = null)
        assertEquals(12, repo.boost("en", "hello"))
        assertEquals(1, repo.count())

        repo.count() // waits for the queued writes
        val fresh = newRepo()
        assertEquals(0, fresh.boost("en", "hello"))
        fresh.load("en")
        assertEquals(12, fresh.boost("en", "hello"))
    }

    @Test fun `boost is capped at 20 uses`() = runBlocking {
        repo.load("en")
        repeat(30) { repo.recordAccepted("en", "hey", prev = null) }
        assertEquals(120, repo.boost("en", "hey"))
    }

    @Test fun `bigram is recorded with prev`() = runBlocking {
        repo.load("en")
        repo.recordAccepted("en", "morning", prev = "Good")
        assertEquals(1, repo.bigram("en", "good", "morning"))
        assertEquals(0, repo.bigram("en", "bad", "morning"))
        repo.recordAccepted("en", "morning", prev = "good")
        assertEquals(2, repo.bigram("en", "good", "morning"))

        repo.count() // waits for the queued writes
        val fresh = newRepo()
        fresh.load("en")
        assertEquals(2, fresh.bigram("en", "good", "morning"))
    }

    @Test fun `block marks the word blocked and persists`() = runBlocking {
        repo.load("en")
        repo.recordAccepted("en", "ducking", prev = null)
        repo.block("en", "Ducking")
        assertTrue(repo.isBlocked("en", "ducking"))
        assertEquals(0, repo.boost("en", "ducking"))
        repo.block("en", "never-typed")
        assertTrue(repo.isBlocked("en", "never-typed"))

        repo.count() // waits for the queued writes
        val fresh = newRepo()
        fresh.load("en")
        assertTrue(fresh.isBlocked("en", "ducking"))
        assertTrue(fresh.isBlocked("en", "never-typed"))
        assertFalse(fresh.isBlocked("en", "hello"))
    }

    @Test fun `a clear while a load is still merging is not undone in memory`() {
        runBlocking {
            repo.recordAccepted("en", "hello", prev = "say")
            repo.count() // on disk
        }
        val fresh = newRepo()
        // The load's caller runs only when told to, so its merge can be held after the read finished.
        val caller = StandardTestDispatcher()
        val loading = CoroutineScope(caller).launch { fresh.load("en") }
        caller.scheduler.runCurrent() // the load queued its read and waits for it
        runBlocking {
            fresh.count() // queued after the read: the read has run and the merge is due
            fresh.clearAll()
        }
        caller.scheduler.runCurrent() // the load resumes and merges what it read before the clear
        assertTrue(loading.isCompleted)
        assertEquals(0, fresh.boost("en", "hello"))
        assertEquals(0, fresh.bigram("en", "say", "hello"))
    }

    @Test fun `clearAll empties the table and the snapshot`() = runBlocking {
        repo.load("en")
        repo.recordAccepted("en", "hello", prev = "say")
        repo.block("en", "nope")
        repo.recordAccepted("es", "hola", prev = null)
        repo.clearAll()
        assertEquals(0, repo.count())
        assertEquals(0, repo.boost("en", "hello"))
        assertEquals(0, repo.bigram("en", "say", "hello"))
        assertFalse(repo.isBlocked("en", "nope"))
        assertEquals(0, repo.boost("es", "hola"))

        repo.count() // waits for the queued writes
        val fresh = newRepo()
        fresh.load("en")
        fresh.load("es")
        assertEquals(0, fresh.boost("en", "hello"))
        assertEquals(0, fresh.boost("es", "hola"))
        assertEquals(0, db.userWordDao().bigramCount())
    }

    @Test fun `the oldest word is evicted at 5001 words`() = runBlocking {
        repo.load("en")
        repo.recordAccepted("es", "hola", prev = null)
        for (i in 0 until UserWordRepository.MAX_WORDS_PER_LOCALE) repo.recordAccepted("en", "w$i", prev = null)
        // Touch the oldest word so the second-oldest is the one evicted.
        repo.recordAccepted("en", "w0", prev = null)
        assertEquals(UserWordRepository.MAX_WORDS_PER_LOCALE + 1, repo.count())

        repo.recordAccepted("en", "newest", prev = "w1")
        assertEquals(UserWordRepository.MAX_WORDS_PER_LOCALE + 1, repo.count())
        assertEquals(0, repo.boost("en", "w1"))
        assertEquals(12, repo.boost("en", "w0"))
        assertEquals(6, repo.boost("en", "newest"))
        assertEquals(6, repo.boost("es", "hola"))

        repo.count() // waits for the queued writes
        val fresh = newRepo()
        fresh.load("en")
        assertEquals(0, fresh.boost("en", "w1"))
        assertEquals(6, fresh.boost("en", "w2"))
        assertEquals(6, fresh.boost("en", "newest"))
    }

    @Test fun `locales are isolated`() = runBlocking {
        repo.load("en")
        repo.load("es")
        repo.recordAccepted("en", "real", prev = "for")
        repo.block("es", "real")
        assertEquals(6, repo.boost("en", "real"))
        assertEquals(0, repo.boost("es", "real"))
        assertEquals(0, repo.bigram("es", "for", "real"))
        assertFalse(repo.isBlocked("en", "real"))
        assertTrue(repo.isBlocked("es", "real"))
    }

    @Test fun `blank words are ignored`() = runBlocking {
        repo.load("en")
        repo.recordAccepted("en", "  ", prev = null)
        repo.recordAccepted("en", "ok", prev = " ")
        assertEquals(1, repo.count())
        assertEquals(0, db.userWordDao().bigramCount())
    }

    @Test fun `entities keep words out of toString`() {
        assertFalse("secret" in UserWordEntity("en", "secret", 1, 1L, false).toString())
        assertFalse("secret" in UserBigramEntity("en", "secret", "secret", 1).toString())
    }
}
