/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.dictionary

import com.manjul.aikeyboard.core.content.FakeContentSource
import com.manjul.aikeyboard.core.content.TestContent
import com.manjul.aikeyboard.core.content.download.PackDownloader
import com.manjul.aikeyboard.core.content.manifest.ManifestCache
import com.manjul.aikeyboard.core.content.manifest.ManifestVerifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The dictionary pack row: picked by kind, installed through the pack work, removed with its files. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DictionaryPackManagerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val scope = TestScope(UnconfinedTestDispatcher())

    private fun files(withManifest: Boolean): Map<String, ByteArray> {
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

    /** Runs the install at once, like a worker with its constraints met. */
    private class FakeWork(private val downloader: PackDownloader) : PackWork {
        val enqueued = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        private val running = MutableStateFlow(false)
        var install: suspend (String) -> Unit = {}

        override fun enqueue(packId: String) {
            enqueued += packId
            running.value = true
        }

        suspend fun finish(packId: String) {
            install(packId)
            running.value = false
        }

        override fun cancel(packId: String) {
            cancelled += packId
            running.value = false
        }

        override fun running(packId: String): Flow<Boolean> = running
    }

    private fun manager(withManifest: Boolean = true): Pair<DictionaryPackManager, FakeWork> {
        val source = FakeContentSource(files(withManifest))
        val cache = ManifestCache(tmp.newFolder(), source, ManifestVerifier(TestContent.publicKeyBase64)) { 1L }
        val root = tmp.newFolder()
        val downloader = PackDownloader(source, root)
        val work = FakeWork(downloader)
        work.install = { id -> downloader.install(cache.current()!!.packs.first { it.id == id }) }
        return DictionaryPackManager(cache, downloader, root, work, scope) to work
    }

    private suspend fun DictionaryPackManager.await(predicate: (PackState) -> Boolean): PackState =
        withTimeout(5_000) { state.first(predicate) }

    @Test fun `the dict pack installs through the pack work and removes again`() = runBlocking {
        val (manager, work) = manager()
        manager.refresh()
        assertEquals(PackState.NotInstalled, manager.await { it == PackState.NotInstalled })
        assertNull(manager.installedFile())

        manager.install()
        manager.await { it is PackState.Downloading }
        assertEquals(listOf("en-fixture"), work.enqueued)
        work.finish("en-fixture")
        assertEquals(PackState.Installed(1), manager.await { it is PackState.Installed })
        assertEquals("en-fixture-1.db", manager.installedFile()?.name)

        manager.remove()
        manager.await { it == PackState.NotInstalled }
        assertNull(manager.installedFile())
        assertTrue("en-fixture" in work.cancelled)
    }

    @Test fun `without a manifest the pack is unavailable`() = runBlocking {
        val (manager, work) = manager(withManifest = false)
        manager.refresh()
        assertEquals(PackState.Unavailable, manager.await { it == PackState.Unavailable })
        manager.install()
        assertTrue(work.enqueued.isEmpty())
    }
}
