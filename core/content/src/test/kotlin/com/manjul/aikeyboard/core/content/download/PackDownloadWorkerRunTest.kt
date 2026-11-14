/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.download

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.AnalyticsSink
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.content.FakeContentSource
import com.manjul.aikeyboard.core.content.TestContent
import com.manjul.aikeyboard.core.content.manifest.ManifestCache
import com.manjul.aikeyboard.core.content.manifest.ManifestVerifier
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** `doWork`: one start event per download, the retry rule, and the result of each outcome. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PackDownloadWorkerRunTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context: Context = RuntimeEnvironment.getApplication()
    private val events = mutableListOf<AnalyticsEvent>()
    private val analytics = object : AnalyticsSink {
        override fun log(event: AnalyticsEvent) {
            events += event
        }
    }

    /** The fixture manifest; the pack files are missing unless [withFiles], so the install fails. */
    private fun deps(withFiles: Boolean): PackDownloadWorker.Deps {
        val files = mutableMapOf(
            ManifestCache.MANIFEST_PATH to TestContent.manifestBytes,
            ManifestCache.SIGNATURE_PATH to TestContent.manifestSig.toByteArray(),
        )
        if (withFiles) {
            TestContent.contentDir.walkTopDown().filter { it.isFile }.forEach {
                files[it.relativeTo(TestContent.contentDir).invariantSeparatorsPath] = it.readBytes()
            }
        }
        val source = FakeContentSource(files)
        val cache = ManifestCache(tmp.newFolder(), source, ManifestVerifier(TestContent.publicKeyBase64)) { 1L }
        return PackDownloadWorker.Deps(cache, PackDownloader(source, tmp.newFolder()), analytics)
    }

    private fun run(deps: PackDownloadWorker.Deps, attempt: Int): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<PackDownloadWorker>(context)
            .setInputData(workDataOf(PackDownloadWorker.KEY_PACK_ID to "starter_plus"))
            .setRunAttemptCount(attempt)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(c: Context, name: String, params: WorkerParameters) =
                    PackDownloadWorker(c, params, deps)
            })
            .build()
            .doWork()
    }

    private fun names() = events.map { it.name }

    @Test fun `a good download logs start and done once`() {
        assertIs<ListenableWorker.Result.Success>(run(deps(withFiles = true), attempt = 0))
        assertEquals(listOf(EventCatalog.PACK_DOWNLOAD_START, EventCatalog.PACK_DOWNLOAD_DONE), names())
        assertEquals("ok", events.last().params["result"])
    }

    @Test fun `the start event is sent on the first attempt only and done after the third`() {
        val deps = deps(withFiles = false)
        assertIs<ListenableWorker.Result.Retry>(run(deps, attempt = 0))
        assertIs<ListenableWorker.Result.Retry>(run(deps, attempt = 1))
        assertIs<ListenableWorker.Result.Failure>(run(deps, attempt = 2))
        assertEquals(listOf(EventCatalog.PACK_DOWNLOAD_START, EventCatalog.PACK_DOWNLOAD_DONE), names())
        assertEquals("failed", events.last().params["result"])
    }

    @Test fun `no event carries a URL or a path`() {
        run(deps(withFiles = true), attempt = 0)
        for (event in events) {
            for (value in event.params.values) {
                val text = value.toString()
                assertEquals(false, text.contains("/") || text.contains("http"), "${event.name}: $text")
            }
        }
    }
}
