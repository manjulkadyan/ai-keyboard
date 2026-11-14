/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.privacy

import android.app.Application
import androidx.room.Room
import androidx.work.Configuration
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.manjul.aikeyboard.core.content.ContentDirs
import com.manjul.aikeyboard.core.content.download.PackDownloadWorker
import com.manjul.aikeyboard.core.content.manifest.ManifestCache
import com.manjul.aikeyboard.core.content.manifest.ManifestVerifier
import com.manjul.aikeyboard.core.content.source.ContentFetchException
import com.manjul.aikeyboard.core.content.source.ContentSource
import com.manjul.aikeyboard.core.database.AppDatabase
import com.manjul.aikeyboard.core.database.DictionaryEntryEntity
import com.manjul.aikeyboard.core.database.StickerEntity
import com.manjul.aikeyboard.core.database.StickerPackEntity
import com.manjul.aikeyboard.core.database.TranslationHistoryEntity
import com.manjul.aikeyboard.core.database.TranslationHistoryRepository
import com.manjul.aikeyboard.core.database.UserPhraseEntity
import com.manjul.aikeyboard.core.database.UserWordRepository
import com.manjul.aikeyboard.core.datastore.FeatureSettings
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.ime.clipboard.provider.ClipboardHistoryDatabase
import com.manjul.aikeyboard.ime.clipboard.provider.ClipboardItem
import com.manjul.aikeyboard.ime.clipboard.provider.ItemType
import com.manjul.aikeyboard.ime.dictionary.FlorisUserDictionaryDatabase
import com.manjul.aikeyboard.ime.dictionary.UserDictionaryEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DataWiperTest {
    private val app = RuntimeEnvironment.getApplication()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
    private val clipDb = Room.inMemoryDatabaseBuilder(app, ClipboardHistoryDatabase::class.java)
        .allowMainThreadQueries().build()
    private val settings = FeatureSettingsStore(app, Dispatchers.IO)
    private val history = TranslationHistoryRepository(db.translationHistoryDao())
    private val userWords = UserWordRepository(db.userWordDao(), scope = scope)

    private var resets = 0
    private var adClears = 0
    private var aiClears = 0
    private var modelDeletes = 0
    private val resetter = object : FirebaseResetter {
        override suspend fun reset() {
            resets++
        }
    }

    /**
     * The Task 1 fixture content, read in place from the path in the test system property (plan F15). Only debug
     * packages it as assets, so beta, release and benchmark would see no manifest. Readable until [offline] is set.
     */
    private val contentSource = object : ContentSource {
        var offline = false
        private val root = File(System.getProperty("content.contentDir")!!)

        override suspend fun fetchTo(path: String, sink: OutputStream, maxBytes: Long): Long {
            if (offline || ".." in path) throw ContentFetchException("unavailable")
            val file = File(root, path).takeIf { it.isFile } ?: throw ContentFetchException("missing")
            val bytes = file.readBytes()
            if (bytes.size > maxBytes) throw ContentFetchException("too large")
            sink.write(bytes)
            return bytes.size.toLong()
        }
    }
    private val manifest = ManifestCache(
        ContentDirs.manifest(app),
        contentSource,
        // Release builds carry no fixture key, so the test takes it from Gradle, not from BuildConfig.
        ManifestVerifier(System.getProperty("content.fixturePublicKey")!!),
        clock = System::currentTimeMillis,
    )
    private val workManager: WorkManager by lazy {
        // WorkManager is a process singleton and may already exist from an earlier test.
        runCatching { WorkManager.initialize(app, Configuration.Builder().build()) }
        WorkManager.getInstance(app).also {
            it.cancelAllWork().result.get()
            it.pruneWork().result.get()
        }
    }

    private var phraseCacheClears = 0

    private suspend fun clearContent() =
        DataWiper.clearContent(db, app, manifest, clearPhraseCache = { phraseCacheClears++ }) { workManager }

    private val wiper = DataWiper(
        settings = settings,
        history = history,
        userWords = userWords,
        clearClipboard = { clipDb.clipboardItemDao().deleteAll() },
        clearAdState = { adClears++ },
        clearAiCache = { aiClears++ },
        deleteModels = { modelDeletes++ },
        firebase = resetter,
        clearUpstreamDictionary = { DataWiper.clearFlorisUserDictionary(app) },
        clearContent = { clearContent() },
    )

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
        clipDb.close()
    }

    private suspend fun seed() {
        db.translationHistoryDao().insert(
            TranslationHistoryEntity(
                sourceText = "hola", targetText = "hello", sourceLang = "es", targetLang = "en",
                engine = "mlkit", origin = "text", createdAt = 1L,
            ),
        )
        db.userWordDao().recordUse("en", "hello", prev = "say", now = 1L, maxWords = 100)
        clipDb.clipboardItemDao().insert(
            ClipboardItem(
                type = ItemType.TEXT, text = "clip", uri = null, creationTimestampMs = 1L,
                isPinned = true, mimeTypes = listOf("text/plain"),
            ),
        )
        settings.update {
            it.copy(
                onboardingDone = true,
                firstOpenAt = 5L,
                consentAiAt = 10L,
                consentVoiceNoticeAt = 11L,
                analyticsOptOut = true,
                privatePackages = setOf("com.bank"),
                keySound = "wood",
                translateHistoryEnabled = false,
                proActive = true,
                proState = "active",
                proExpiresAt = 99L,
                proIssuedAt = 98L,
                proSignature = "sig",
                proCheckedAt = 97L,
            )
        }
    }

    @Test
    fun `wipe empties history, the user dictionary and the clipboard`() = runBlocking {
        seed()
        assertEquals(1, db.translationHistoryDao().count())
        assertEquals(1, db.userWordDao().count())

        assertTrue(wiper.wipeLocal(includeModels = false))

        assertEquals(0, db.translationHistoryDao().count())
        assertEquals(0, db.userWordDao().count())
        assertEquals(0, db.userWordDao().bigramCount())
        assertEquals(emptyList(), clipDb.clipboardItemDao().getAll())
    }

    @Test
    fun `wipe empties the content tables and deletes the downloaded packs and the manifest cache`() = runBlocking {
        val phraseId = db.phraseDao().insert(
            UserPhraseEntity(text = "see you", translation = null, targetLang = null, createdAt = 1, updatedAt = 1),
        )
        db.phraseDao().toggleFavourite(UserPhraseEntity.ref(phraseId), now = 2)
        db.phraseDao().toggleFavourite("c:greet-1", now = 2)
        db.dictionaryDao().insert(
            listOf(
                DictionaryEntryEntity(
                    lang = "en", headword = "zork", headwordNorm = "zork", pos = "noun", definition = "d",
                    example = null, source = DictionaryEntryEntity.SOURCE_ONLINE, fetchedAt = 1,
                ),
            ),
        )
        db.stickerDao().upsertPack(
            StickerPackEntity("p1", "P", 1, isPremium = false, manifestUrl = "m", downloaded = true, updatedAt = 1),
        )
        db.stickerDao().upsertStickers(listOf(StickerEntity("p1/01", "p1", "/f", "image/png", "k")))
        val pack = File(ContentDirs.packs(app), "stickers/p1/1/01.png").apply { parentFile!!.mkdirs(); writeText("x") }
        val manifest = File(ContentDirs.manifest(app), "manifest.json").apply { parentFile!!.mkdirs(); writeText("{}") }

        assertTrue(wiper.wipeLocal(includeModels = false))

        assertEquals(emptyList(), db.phraseDao().observePhrases().first())
        assertFalse(db.phraseDao().isFavourite("c:greet-1"))
        assertEquals(emptyList(), db.dictionaryDao().find("en", "zork"))
        assertEquals(emptyList(), db.stickerDao().packs())
        assertEquals(emptyList(), db.stickerDao().stickers("p1"))
        assertFalse(pack.exists())
        assertFalse(manifest.exists())
        assertFalse(ContentDirs.packs(app).exists())
    }

    @Test
    fun `wipe cancels the pack downloads and forgets the in-memory manifest`() = runBlocking {
        val request = OneTimeWorkRequestBuilder<PackDownloadWorker>()
            .addTag(PackDownloadWorker.TAG)
            .setInitialDelay(1, TimeUnit.DAYS) // stays enqueued: only the wipe can stop it
            .build()
        workManager.enqueue(request).result.get()
        assertTrue(manifest.current() != null, "the fixture manifest loads")

        assertTrue(wiper.wipeLocal(includeModels = false))

        assertEquals(WorkInfo.State.CANCELLED, workManager.getWorkInfoById(request.id).get()!!.state)
        // With no network, a manifest still held in memory would come back here.
        contentSource.offline = true
        assertEquals(null, manifest.current())
        assertFalse(ContentDirs.manifest(app).exists())
    }

    @Test
    fun `wipe clears the in-memory phrase translations`() = runBlocking {
        // Review M2: the phrasebook's translation cache holds phrase text, so Delete my data drops it.
        assertTrue(wiper.wipeLocal(includeModels = false))
        assertEquals(1, phraseCacheClears)
    }

    @Test
    fun `contentWifiOnly goes back to its default`() = runBlocking {
        settings.update { it.copy(contentWifiOnly = false) }
        wiper.wipeLocal(includeModels = false)
        assertEquals(true, settings.current.contentWifiOnly)
    }

    @Test
    fun `settings go back to the defaults but keep onboarding, the opt-out and the entitlement cache`() =
        runBlocking {
            seed()
            wiper.wipeLocal(includeModels = false)

            val expected = FeatureSettings(
                onboardingDone = true,
                analyticsOptOut = true,
                proActive = true,
                proState = "active",
                proExpiresAt = 99L,
                proIssuedAt = 98L,
                proSignature = "sig",
                proCheckedAt = 97L,
            )
            assertEquals(expected, settings.flow.first())
            assertEquals(expected, settings.current)
        }

    @Test
    fun `wipe empties the upstream user dictionary even when DictionaryManager never loaded it`() = runBlocking {
        // The real file, as the IME or the user dictionary screen would have created it. DictionaryManager is not
        // touched, so its DAO stays null, as on the Delete my data screen.
        val seeded = Room.databaseBuilder(
            app,
            FlorisUserDictionaryDatabase::class.java,
            FlorisUserDictionaryDatabase.DB_FILE_NAME,
        ).allowMainThreadQueries().build()
        seeded.userDictionaryDao().insert(
            UserDictionaryEntry(id = 0, word = "zorblax", freq = 255, locale = "en", shortcut = null),
        )
        seeded.close()

        assertTrue(wiper.wipeLocal(includeModels = false))

        val check = Room.databaseBuilder(
            app,
            FlorisUserDictionaryDatabase::class.java,
            FlorisUserDictionaryDatabase.DB_FILE_NAME,
        ).allowMainThreadQueries().build()
        try {
            assertEquals(emptyList(), check.userDictionaryDao().queryAll())
        } finally {
            check.close()
        }
    }

    @Test
    fun `no upstream user dictionary file is not a failure and creates none`() = runBlocking {
        assertTrue(wiper.wipeLocal(includeModels = false))
        assertFalse(app.getDatabasePath(FlorisUserDictionaryDatabase.DB_FILE_NAME).exists())
    }

    @Test
    fun `firebase reset, ad state and AI cache run once and models stay unless asked`() = runBlocking {
        seed()
        wiper.wipeLocal(includeModels = false)
        assertEquals(1, resets)
        assertEquals(1, adClears)
        assertEquals(1, aiClears)
        assertEquals(0, modelDeletes)

        wiper.wipeLocal(includeModels = true)
        assertEquals(1, modelDeletes)
    }

    @Test
    fun `a failing step does not stop the others and is reported`() = runBlocking {
        seed()
        val failing = DataWiper(
            settings = settings,
            history = history,
            userWords = userWords,
            clearClipboard = { error("disk") },
            clearAdState = { adClears++ },
            clearAiCache = { aiClears++ },
            deleteModels = { modelDeletes++ },
            firebase = resetter,
            clearUpstreamDictionary = {},
            clearContent = { clearContent() },
        )
        assertEquals(false, failing.wipeLocal(includeModels = false))
        assertEquals(0, db.translationHistoryDao().count())
        assertEquals(1, resets)
        assertEquals(1, adClears)
    }
}
