/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.dictionary

import android.app.Application
import androidx.room.Room
import com.manjul.aikeyboard.core.ai.FakeTokenSource
import com.manjul.aikeyboard.core.ai.kb.DictionaryClient
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.AnalyticsSink
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.content.TestContent
import com.manjul.aikeyboard.core.database.AppDatabase
import com.manjul.aikeyboard.core.database.DictionaryDao
import com.manjul.aikeyboard.core.database.DictionaryEntryEntity
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The pack first, then kb-api for a missing word only when the app allows the network, `onlineDictionary` is on and
 * AI consent is given (plan F1). Online answers are cached for 30 days. One `dictionary_lookup` per query (F16), and
 * the word never reaches a log line or an exception (F17).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DictionaryRepositoryTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
    private val server = MockWebServer()
    private val settings = FeatureSettingsStore(app)
    private val events = mutableListOf<AnalyticsEvent>()
    private val analytics = object : AnalyticsSink {
        override fun log(event: AnalyticsEvent) {
            events += event
        }
    }
    private val pack = File(TestContent.contentDir, "dict/en-fixture-1.db")
    private val reader = DictionaryPackReader.open(pack)
    private var now = 10L * DAY
    private var clientCreated = 0

    private val client by lazy {
        DictionaryClient(OkHttpClient(), server.url("/").toString(), FakeTokenSource())
    }

    @Before fun setUp() = runBlocking {
        ShadowLog.clear()
        server.start()
        settings.update { it.copy(consentAiAt = 1L, onlineDictionary = true) }
    }

    @After fun tearDown() {
        reader?.close()
        db.close()
        server.shutdown()
    }

    private fun repo(
        reader: () -> DictionaryPackReader? = { this.reader },
        dao: DictionaryDao = db.dictionaryDao(),
    ) = DictionaryRepository(
        reader = reader,
        dao = dao,
        client = {
            clientCreated++
            client
        },
        settings = settings,
        analytics = analytics,
        clock = { now },
    )

    private fun found(headword: String) = MockResponse().setResponseCode(200).setBody(
        """{"entries":[{"headword":"$headword","pos":"noun","definition":"A striped animal.",""" +
            """"example":"The $headword ran.","synonyms":["quagga"]}],"source":"fake",""" +
            """"licence_url":"https://example.com/fake-dictionary-licence"}""",
    )

    private fun lookups() = events.filter { it.name == EventCatalog.DICTIONARY_LOOKUP }.map { it.params }

    @Test fun `a word in the pack makes no client call and logs offline found`() = runBlocking {
        val result = repo().lookup("Run", allowNetwork = true)
        assertEquals(4, result.definitions.size)
        assertTrue(result.offline)
        assertEquals("AI Keyboard contributors", result.attribution?.attribution)
        assertEquals(0, server.requestCount)
        assertEquals(0, clientCreated)
        assertEquals(listOf(mapOf<String, Any>("offline" to true, "found" to true)), lookups())
    }

    @Test fun `a missing word calls the client once and the cached answer serves the second lookup`() = runBlocking {
        server.enqueue(found("zebra"))
        val repo = repo()
        val first = repo.lookup("Zebra", allowNetwork = true)
        assertEquals(listOf("A striped animal."), first.definitions.map { it.definition })
        assertEquals(listOf("quagga"), first.definitions.single().synonyms)
        assertFalse(first.offline)
        assertEquals("https://example.com/fake-dictionary-licence", first.attribution?.licenceUrl)
        assertEquals(1, server.requestCount)
        assertEquals(1, db.dictionaryDao().find("en", "zebra").size)

        val second = repo.lookup(" zebra ", allowNetwork = true)
        assertEquals(first.definitions, second.definitions)
        assertNotNull(second.attribution)
        assertEquals(1, server.requestCount)
        // F16: `offline` means no online answer was used, and a cached answer is an online one.
        assertFalse(second.offline)
        assertEquals(
            listOf(mapOf<String, Any>("offline" to false, "found" to true), mapOf("offline" to false, "found" to true)),
            lookups(),
        )
    }

    @Test fun `a cache row older than 30 days counts as missing`() = runBlocking {
        server.enqueue(found("zebra"))
        server.enqueue(found("zebra"))
        val repo = repo()
        repo.lookup("zebra", allowNetwork = true)
        now += 31 * DAY
        val again = repo.lookup("zebra", allowNetwork = true)
        assertEquals(1, again.definitions.size)
        assertEquals(2, server.requestCount)
        // The stale rows are replaced, not kept next to the fresh ones.
        assertEquals(1, db.dictionaryDao().find("en", "zebra").size)
    }

    @Test fun `every lookup that leases the pack reader releases it once, after the read`() = runBlocking {
        val released = mutableListOf<DictionaryPackReader>()
        val pack = assertNotNull(reader)
        val repo = DictionaryRepository(
            reader = { pack }, dao = db.dictionaryDao(), client = { null }, settings = settings,
            analytics = analytics, clock = { now }, release = { released += it },
        )
        assertEquals("run", repo.lookupOffline("run").definitions.first().headword)
        assertEquals(1, released.size)
        repo.lookup("run", allowNetwork = false)
        repo.lookup("zebra", allowNetwork = false)
        assertEquals(listOf(pack, pack, pack), released)
    }

    @Test fun `lookupOffline never calls the client`() = runBlocking {
        server.enqueue(found("zebra"))
        val repo = repo()
        val result = repo.lookupOffline("zebra")
        assertTrue(result.definitions.isEmpty())
        assertTrue(result.offline)
        assertEquals("run", repo.lookupOffline("run").definitions.first().headword)
        assertEquals(0, server.requestCount)
        assertEquals(0, clientCreated)
        assertEquals(listOf(false, true), lookups().map { it["found"] })
    }

    @Test fun `allowNetwork false makes no call`() = runBlocking {
        repo().lookup("zebra", allowNetwork = false)
        assertEquals(0, server.requestCount)
    }

    @Test fun `onlineDictionary off makes no call`() = runBlocking {
        settings.update { it.copy(onlineDictionary = false) }
        val result = repo().lookup("zebra", allowNetwork = true)
        assertTrue(result.definitions.isEmpty())
        assertEquals(0, server.requestCount)
        assertEquals(listOf(mapOf<String, Any>("offline" to true, "found" to false)), lookups())
    }

    @Test fun `no AI consent makes no call`() = runBlocking {
        settings.update { it.copy(consentAiAt = 0L) }
        repo().lookup("zebra", allowNetwork = true)
        assertEquals(0, server.requestCount)
        assertEquals(0, clientCreated)
    }

    @Test fun `Unavailable gives an empty offline result`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        val result = repo().lookup("zebra", allowNetwork = true)
        assertTrue(result.definitions.isEmpty())
        assertTrue(result.offline)
        assertNull(result.attribution)
        assertEquals(1, server.requestCount)
        assertEquals(listOf(mapOf<String, Any>("offline" to true, "found" to false)), lookups())
        assertTrue(db.dictionaryDao().find("en", "zebra").isEmpty())
    }

    @Test fun `no pack still reaches kb-api and says the pack is missing`() = runBlocking {
        server.enqueue(found("run"))
        val result = repo(reader = { null }).lookup("run", allowNetwork = true)
        assertTrue(result.packMissing)
        assertEquals(1, result.definitions.size)
        assertTrue(repo(reader = { null }).lookupOffline("run").packMissing)
        assertFalse(repo().lookupOffline("run").packMissing)
    }

    @Test fun `the looked up word reaches no log line and no exception message`() = runBlocking {
        val marker = "qzmarkerword"
        val thrown = mutableListOf<Throwable>()
        suspend fun attempt(block: suspend () -> Unit) {
            try {
                block()
            } catch (e: Throwable) {
                thrown += e
            }
        }
        // Found online, not found, Unavailable and a failing pack and cache.
        server.enqueue(found(marker))
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(503))
        attempt { repo().lookup(marker, allowNetwork = true) }
        attempt { repo().lookup(marker + "x", allowNetwork = true) }
        attempt { repo().lookup(marker + "y", allowNetwork = true) }
        val broken = BrokenDao()
        attempt { repo(reader = { throw IllegalStateException("pack broken") }, dao = broken).lookup(marker, true) }
        attempt { repo(reader = { throw IllegalStateException("pack broken") }, dao = broken).lookupOffline(marker) }

        assertTrue(thrown.isEmpty(), "lookups never throw")
        val logged = ShadowLog.getLogs().flatMap { listOf(it.msg.orEmpty(), it.throwable?.toString().orEmpty()) }
        assertTrue(logged.none { it.contains(marker, ignoreCase = true) })
        assertTrue(events.none { e -> e.params.values.any { it.toString().contains(marker, ignoreCase = true) } })
        assertEquals(5, lookups().size)
    }

    private class BrokenDao : DictionaryDao {
        override suspend fun insert(entries: List<DictionaryEntryEntity>) = throw IllegalStateException("disk full")

        override suspend fun find(lang: String, headwordNorm: String): List<DictionaryEntryEntity> =
            throw IllegalStateException("disk full")

        override suspend fun delete(lang: String, headwordNorm: String) = throw IllegalStateException("disk full")

        override suspend fun clearAll() = Unit
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}
