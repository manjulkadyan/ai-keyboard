/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.stickers

import androidx.room.Room
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.AnalyticsSink
import com.manjul.aikeyboard.core.common.Outcome
import com.manjul.aikeyboard.core.content.FakeContentSource
import com.manjul.aikeyboard.core.content.TestContent
import com.manjul.aikeyboard.core.content.download.PackDownloader
import com.manjul.aikeyboard.core.content.manifest.ManifestCache
import com.manjul.aikeyboard.core.content.manifest.ManifestVerifier
import com.manjul.aikeyboard.core.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The sticker repository: the bundled starter pack, manifest packs, and the hash-checked install (S5 Task 6). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StickerRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val events = mutableListOf<AnalyticsEvent>()
    private var sinkThrows = false
    private val sink = object : AnalyticsSink {
        override fun log(event: AnalyticsEvent) {
            check(!sinkThrows) { "sink failed" }
            events += event
        }
    }

    @After fun close() = db.close()

    private fun files(withManifest: Boolean): MutableMap<String, ByteArray> {
        val files = mutableMapOf<String, ByteArray>()
        TestContent.contentDir.walkTopDown().filter { it.isFile }.forEach {
            files[it.relativeTo(TestContent.contentDir).invariantSeparatorsPath] = it.readBytes()
        }
        if (!withManifest) {
            files.remove(ManifestCache.MANIFEST_PATH)
            files.remove(ManifestCache.SIGNATURE_PATH)
        }
        return files
    }

    private val starter = object : BundledPacks {
        override fun packs(): List<StickerPackUi> {
            val dir = tmp.root.resolve("bundled/starter").apply { mkdirs() }
            val stickers = (1..2).map { n ->
                val file = File(dir, "0$n.png").apply { writeBytes(byteArrayOf(n.toByte())) }
                StickerUi(id = "starter/0$n", packId = "starter", file = file, premium = false)
            }
            return listOf(StickerPackUi("starter", "Starter", premium = false, installed = true, stickers = stickers))
        }
    }

    private var source = FakeContentSource()

    private fun repository(files: Map<String, ByteArray>, canDownload: () -> Boolean = { true }): StickerRepository {
        source = FakeContentSource(files)
        val cache = ManifestCache(tmp.newFolder(), source, ManifestVerifier(TestContent.publicKeyBase64)) { 1L }
        val downloader = PackDownloader(source, tmp.newFolder())
        return StickerRepository(cache, downloader, db.stickerDao(), starter, sink, canDownload = canDownload)
    }

    @Test fun `the starter pack is listed with no manifest`() = runBlocking {
        val repo = repository(files(withManifest = false))
        repo.refresh()
        val packs = repo.packs.value
        assertEquals(listOf("starter"), packs.map { it.id })
        assertTrue(packs.single().installed)
        assertEquals(2, packs.single().stickers.size)
    }

    @Test fun `the manifest pack is listed but not installed until install`() = runBlocking {
        val repo = repository(files(withManifest = true))
        repo.refresh()
        val plus = repo.packs.value.single { it.id == "starter_plus" }
        assertTrue(plus.premium)
        assertFalse(plus.installed)
        assertEquals("starter", repo.packs.value.first().id)
    }

    @Test fun `install stores the pack and its stickers`() = runBlocking {
        val repo = repository(files(withManifest = true))
        repo.refresh()
        assertIs<Outcome.Success<Unit>>(repo.install("starter_plus"))

        val pack = db.stickerDao().packs().single()
        assertEquals("starter_plus", pack.id)
        assertTrue(pack.isPremium)
        assertTrue(pack.downloaded)
        val stickers = db.stickerDao().stickers("starter_plus")
        assertEquals(8, stickers.size)
        assertTrue(stickers.all { File(it.filePath).isFile && it.mime == "image/png" })

        val plus = repo.packs.value.single { it.id == "starter_plus" }
        assertTrue(plus.installed)
        assertEquals(8, plus.stickers.size)
        assertTrue(plus.stickers.all { it.premium })

        assertEquals(listOf("pack_download_start", "pack_download_done"), events.map { it.name })
        assertTrue(events.all { it.params["lang"] == "mul" && it.params["kind"] == "stickers" })
        assertEquals("ok", events.last().params["result"])
        assertTrue(events.none { e -> e.params.values.any { it.toString().contains("/") } })
    }

    @Test fun `a bad hash stores nothing`() = runBlocking {
        val files = files(withManifest = true)
        files["stickers/starter_plus/01.png"] = byteArrayOf(1, 2, 3)
        val repo = repository(files)
        repo.refresh()
        assertIs<Outcome.Failure>(repo.install("starter_plus"))
        assertTrue(db.stickerDao().packs().isEmpty())
        assertTrue(db.stickerDao().stickers("starter_plus").isEmpty())
        assertFalse(repo.packs.value.single { it.id == "starter_plus" }.installed)
        assertEquals("hash_mismatch", events.last().params["result"])
    }

    @Test fun `wifi only on a metered network downloads nothing`() = runBlocking {
        val repo = repository(files(withManifest = true), canDownload = { false })
        repo.refresh()
        val before = source.requests.size
        val outcome = assertIs<Outcome.Failure>(repo.install("starter_plus"))
        assertIs<WaitsForWifiException>(outcome.error)
        assertEquals(before, source.requests.size)
        assertTrue(events.isEmpty())
        assertTrue(db.stickerDao().packs().isEmpty())
        assertFalse(repo.packs.value.single { it.id == "starter_plus" }.installed)
    }

    @Test fun `an install that throws gives a failure and no crash`() = runBlocking {
        val repo = repository(files(withManifest = true))
        repo.refresh()
        sinkThrows = true // an exception outside the downloader, which reports its own errors as a Failure
        assertIs<Outcome.Failure>(repo.install("starter_plus"))
        assertFalse(repo.packs.value.single { it.id == "starter_plus" }.installed)
    }

    @Test fun `the bundled pack wins on a duplicate id`() = runBlocking {
        val repo = repository(files(withManifest = false))
        db.stickerDao().upsertPack(
            com.manjul.aikeyboard.core.database.StickerPackEntity("starter", "Other", 9, true, "", true, 1L),
        )
        repo.refresh()
        val pack = repo.packs.value.single()
        assertEquals("Starter", pack.title)
        assertFalse(pack.premium)
    }
}
