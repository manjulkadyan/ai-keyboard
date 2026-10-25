/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate.mlkit

import androidx.work.WorkInfo
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.translate.LangPair
import com.manjul.aikeyboard.core.translate.ModelSizes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
class TranslateModelStoreTest {
    private val context = RuntimeEnvironment.getApplication()
    private val en = Lang.English
    private val ur = Lang("ur")
    private val hi = Lang("hi")

    @Test fun downloadEnqueuesEveryMissingLanguageOnce() {
        val r = ModelStoreRig(context)
        r.store.download(LangPair(en, ur))
        r.store.download(LangPair(en, ur))
        assertEquals(1, r.jobs(ur).size)
        assertEquals(1, r.jobs(en).size)
        assertEquals(ModelUiState.DOWNLOADING, r.store.state(ur))
        assertEquals(ModelUiState.DOWNLOADING, r.store.state(en))
        assertTrue(r.models.downloads.isEmpty(), "nothing downloads before the job runs")
    }

    @Test fun downloadSkipsReadyAndUnsupportedLanguages() = runBlocking {
        val r = ModelStoreRig(context, FakeRemoteModels(setOf(en)))
        r.store.refresh()
        r.store.download(LangPair(Lang("ps"), en))
        assertTrue(r.jobs(Lang("ps")).isEmpty())
        assertTrue(r.jobs(en).isEmpty())
    }

    @Test fun missingBytesCountsThePivot() = runBlocking {
        val r = ModelStoreRig(context)
        r.store.refresh()
        assertEquals(3 * ModelSizes.APPROX_MODEL_BYTES, r.store.missingBytes(LangPair(ur, hi)))
        assertEquals(90L * 1024 * 1024, r.store.missingBytes(LangPair(ur, hi)))
    }

    @Test fun refreshMapsDownloadedToReady() = runBlocking {
        val r = ModelStoreRig(context, FakeRemoteModels(setOf(en, ur)))
        assertEquals(emptyMap(), r.store.states.value)
        r.store.refresh()
        assertEquals(mapOf(en to ModelUiState.READY, ur to ModelUiState.READY), r.store.states.value)
        assertEquals(0L, r.store.missingBytes(LangPair(en, ur)))
    }

    @Test fun englishCannotBeDeletedWhileAnotherLanguageNeedsIt() = runBlocking {
        val r = ModelStoreRig(context, FakeRemoteModels(setOf(en, ur)))
        r.store.refresh()
        assertFalse(r.store.canDelete(en))
        assertFalse(r.store.delete(en))
        assertTrue(r.models.deletes.isEmpty())
        assertTrue(r.store.delete(ur))
        assertEquals(ModelUiState.ABSENT, r.store.state(ur))
        assertTrue(r.store.canDelete(en))
        assertTrue(r.store.delete(en))
    }

    @Test fun stopBeforeTheJobRunsCancelsIt() = runBlocking {
        val r = ModelStoreRig(context)
        r.store.download(ur)
        r.store.stop(ur)
        assertEquals(ModelUiState.ABSENT, r.store.state(ur))
        assertEquals(WorkInfo.State.CANCELLED, r.jobs(ur).single().state)
        assertEquals(setOf("ur"), r.settings.flow.first().translateStopped)
        // The job never runs, so the next refresh drops the flag.
        r.store.refresh()
        assertEquals(emptySet(), r.settings.flow.first().translateStopped)
        assertTrue(r.models.downloads.isEmpty())
    }

    @Test fun downloadingAgainAfterStopClearsTheFlag() = runBlocking {
        val r = ModelStoreRig(context)
        r.store.download(ur)
        r.store.stop(ur)
        r.store.download(ur)
        assertEquals(ModelUiState.DOWNLOADING, r.store.state(ur))
        assertEquals(emptySet(), r.settings.flow.first().translateStopped)
        assertEquals(1, r.jobs(ur).count { !it.state.isFinished })
    }

    @Test fun refreshDeletesAStoppedModelWhoseJobIsGone() = runBlocking {
        val r = ModelStoreRig(context, FakeRemoteModels(setOf(en, ur)))
        r.updateSettings { it.copy(translateStopped = setOf("ur")) }
        r.store.refresh()
        assertEquals(listOf(ur), r.models.deletes)
        assertEquals(ModelUiState.ABSENT, r.store.state(ur))
        assertEquals(emptySet(), r.settings.flow.first().translateStopped)
    }

    @Test fun refreshRestoresPendingDownloadsAfterARestart() = runBlocking {
        val r = ModelStoreRig(context)
        r.store.download(hi)
        val restarted = TranslateModelStore(r.models, { r.workManager }, r.settings, {}, CoroutineScope(Dispatchers.Unconfined))
        assertEquals(ModelUiState.ABSENT, restarted.state(hi))
        restarted.refresh()
        assertEquals(ModelUiState.DOWNLOADING, restarted.state(hi))
    }
}
