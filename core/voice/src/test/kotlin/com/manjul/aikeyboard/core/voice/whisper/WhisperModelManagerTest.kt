/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.voice.whisper

import android.content.Context
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WhisperModelManagerTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val server = MockWebServer()
    private val fakeModel = ByteArray(1024) { (it * 31 + 7).toByte() }
    private val fakeSha = MessageDigest.getInstance("SHA-256").digest(fakeModel).joinToString("") { "%02x".format(it) }
    private val events = mutableListOf<AnalyticsEvent>()
    private lateinit var workManager: WorkManager
    private var current: WhisperModelManager? = null

    @Before fun setUp() {
        server.start()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .setTaskExecutor(SynchronousExecutor())
                .setWorkerFactory(ModelDownloadWorkerFactory { current!! })
                .build(),
        )
        workManager = WorkManager.getInstance(context)
    }

    @After fun tearDown() {
        server.shutdown()
        File(context.noBackupFilesDir, "models").deleteRecursively()
    }

    private fun manager(ramBytes: Long = 8L shl 30, sha: String = fakeSha) = WhisperModelManager(
        noBackupDir = context.noBackupFilesDir,
        workManager = workManager,
        ramBytes = ramBytes,
        baseUrl = server.url("/models/").toString(),
        pinnedSha256 = { sha },
        analytics = { events += it },
        scope = CoroutineScope(Dispatchers.Unconfined),
    ).also { current = it }

    private fun serveModel() = server.enqueue(MockResponse().setBody(Buffer().write(fakeModel)))

    /** Runs the enqueued download now (the test driver stands in for the network constraint). */
    private fun runDownload(m: WhisperModelManager, spec: WhisperModelSpec) {
        val info = workManager.getWorkInfosForUniqueWork(m.workName(spec)).get().single()
        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(info.id)
    }

    private fun modelFile(spec: WhisperModelSpec) = File(File(context.noBackupFilesDir, "models"), spec.fileName)

    @Test fun downloadWithTheRightHashBecomesReadyUnderNoBackupModels() {
        val m = manager()
        serveModel()
        m.download(WhisperModelSpec.BASE, wifiOnly = false)
        assertEquals(ModelState.DOWNLOADING, m.states.value[WhisperModelSpec.BASE])
        runDownload(m, WhisperModelSpec.BASE)

        assertEquals(ModelState.READY, m.states.value[WhisperModelSpec.BASE])
        val file = modelFile(WhisperModelSpec.BASE)
        assertTrue(file.isFile)
        assertTrue(fakeModel.contentEquals(file.readBytes()))
        assertEquals(File(context.noBackupFilesDir, "models").canonicalPath, file.parentFile!!.canonicalPath)
        assertFalse(File(file.path + ".part").exists())
        assertEquals("/models/ggml-base-q5_1.bin", server.takeRequest().requestUrl!!.encodedPath)
        assertEquals(WhisperModelSpec.BASE, m.readyModel())
        assertEquals(file.path, m.readyModelPath())
    }

    @Test fun wrongHashFailsAndLeavesNoFile() {
        val m = manager(sha = "0".repeat(64))
        serveModel()
        m.download(WhisperModelSpec.BASE, wifiOnly = false)
        runDownload(m, WhisperModelSpec.BASE)

        assertEquals(ModelState.FAILED, m.states.value[WhisperModelSpec.BASE])
        assertFalse(modelFile(WhisperModelSpec.BASE).exists())
        assertFalse(File(modelFile(WhisperModelSpec.BASE).path + ".part").exists())
        assertNull(m.readyModel())
    }

    @Test fun anUnpinnedHashFailsClosedWithoutDownloading() {
        val m = manager(sha = "")
        serveModel()
        m.download(WhisperModelSpec.BASE, wifiOnly = false)
        runDownload(m, WhisperModelSpec.BASE)

        assertEquals(ModelState.FAILED, m.states.value[WhisperModelSpec.BASE])
        assertFalse(modelFile(WhisperModelSpec.BASE).exists())
        assertEquals(0, server.requestCount)
    }

    @Test fun wifiOnlyRequiresAnUnmeteredNetwork() {
        val m = manager()
        m.download(WhisperModelSpec.BASE, wifiOnly = true)
        val info = workManager.getWorkInfosForUniqueWork(m.workName(WhisperModelSpec.BASE)).get().single()
        assertEquals(NetworkType.UNMETERED, info.constraints.requiredNetworkType)
        m.delete(WhisperModelSpec.BASE)
        m.download(WhisperModelSpec.BASE, wifiOnly = false)
        val any = workManager.getWorkInfosForUniqueWork(m.workName(WhisperModelSpec.BASE)).get().last()
        assertEquals(NetworkType.CONNECTED, any.constraints.requiredNetworkType)
    }

    @Test fun smallIsOfferedOnlyWithSixGibOfRam() {
        assertEquals(listOf(WhisperModelSpec.BASE), manager(ramBytes = 4L shl 30).offered())
        assertEquals(listOf(WhisperModelSpec.BASE, WhisperModelSpec.SMALL), manager(ramBytes = 8L shl 30).offered())
    }

    @Test fun deleteMakesTheModelAbsent() {
        val m = manager()
        serveModel()
        m.download(WhisperModelSpec.BASE, wifiOnly = false)
        runDownload(m, WhisperModelSpec.BASE)
        assertEquals(ModelState.READY, m.states.value[WhisperModelSpec.BASE])

        m.delete(WhisperModelSpec.BASE)
        assertEquals(ModelState.ABSENT, m.states.value[WhisperModelSpec.BASE])
        assertFalse(modelFile(WhisperModelSpec.BASE).exists())
        assertNull(m.readyModelPath())
    }

    @Test fun aModelOnDiskIsReadyAfterARestart() {
        modelFile(WhisperModelSpec.BASE).apply { parentFile!!.mkdirs(); writeBytes(fakeModel) }
        assertEquals(ModelState.READY, manager().states.value[WhisperModelSpec.BASE])
        assertEquals(ModelState.ABSENT, manager().states.value[WhisperModelSpec.SMALL])
    }

    @Test fun downloadsReportStartAndDoneWithClosedValues() {
        val m = manager()
        serveModel()
        m.download(WhisperModelSpec.BASE, wifiOnly = false)
        runDownload(m, WhisperModelSpec.BASE)

        assertEquals(listOf("pack_download_start", "pack_download_done"), events.map { it.name })
        assertEquals(mapOf("lang" to "mul", "kind" to "whisper", "size_bucket" to "50-100"), events[0].params)
        assertEquals("ok", events[1].params["result"])
        val text = events.joinToString { it.params.values.joinToString() }
        assertFalse("http" in text || "/" in text, "no URL or path in events: $text")
    }
}
