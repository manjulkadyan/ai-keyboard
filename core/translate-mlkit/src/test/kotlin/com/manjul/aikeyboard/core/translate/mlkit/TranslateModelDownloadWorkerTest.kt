/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate.mlkit

import androidx.work.NetworkType
import androidx.work.WorkInfo
import com.manjul.aikeyboard.core.translate.Lang
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TranslateModelDownloadWorkerTest {
    private val context = RuntimeEnvironment.getApplication()
    private val ur = Lang("ur")

    @Test fun successMarksReadyAndLogsOk() {
        val r = ModelStoreRig(context)
        r.store.download(ur)
        assertEquals(WorkInfo.State.SUCCEEDED, r.runJob(ur).state)
        assertEquals(ModelUiState.READY, r.store.state(ur))
        assertEquals(listOf(ur to true), r.models.downloads)
        val start = r.named("pack_download_start").single().params
        assertEquals("translate", start["kind"])
        assertEquals("ur", start["lang"])
        val done = r.named("pack_download_done").single().params
        assertEquals("ok", done["result"])
        assertEquals("translate", done["kind"])
    }

    @Test fun failureMarksFailedAndLogsFailed() {
        val r = ModelStoreRig(context, FakeRemoteModels().apply { failWith = IOException("secret") })
        r.store.download(ur)
        assertEquals(WorkInfo.State.FAILED, r.runJob(ur).state)
        assertEquals(ModelUiState.FAILED, r.store.state(ur))
        assertEquals("failed", r.named("pack_download_done").single().params["result"])
        // A failed language can be downloaded again.
        r.store.download(ur)
        assertEquals(ModelUiState.DOWNLOADING, r.store.state(ur))
    }

    @Test fun languageStoppedDuringTheDownloadIsDeletedAfterIt() = runBlocking {
        val r = ModelStoreRig(context)
        // ML Kit cannot cancel: the user's Stop lands while the download runs.
        r.models.onDownload = { r.settings.update { it.copy(translateStopped = it.translateStopped + "ur") } }
        r.store.download(ur)
        r.runJob(ur)
        assertEquals(listOf(ur), r.models.deletes)
        assertTrue(ur !in r.models.present)
        assertEquals(ModelUiState.ABSENT, r.store.state(ur))
        assertEquals(emptySet(), r.settings.flow.first().translateStopped)
        assertEquals("cancelled", r.named("pack_download_done").single().params["result"])
    }

    @Test fun wifiOnlyRequiresAnUnmeteredNetwork() {
        val r = ModelStoreRig(context)
        r.store.download(ur)
        assertEquals(NetworkType.UNMETERED, r.jobs(ur).single().constraints.requiredNetworkType)
    }

    @Test fun mobileDataAllowedRequiresAnyNetwork() {
        val r = ModelStoreRig(context)
        r.updateSettings { it.copy(translateWifiOnly = false) }
        r.store.download(ur)
        assertEquals(NetworkType.CONNECTED, r.jobs(ur).single().constraints.requiredNetworkType)
        r.runJob(ur)
        assertEquals(listOf(ur to false), r.models.downloads)
    }

    @Test fun inputDataIsTheLanguageTagOnly() {
        val r = ModelStoreRig(context)
        r.store.download(ur)
        r.runJob(ur)
        val input = r.workerParams.single().inputData.keyValueMap
        assertEquals(mapOf<String, Any>(TranslateModelDownloadWorker.KEY_LANG to "ur"), input)
    }
}
