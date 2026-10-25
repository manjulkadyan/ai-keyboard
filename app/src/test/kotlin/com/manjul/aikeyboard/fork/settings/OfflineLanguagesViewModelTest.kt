/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.settings

import android.provider.Settings
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.translate.ModelSizes
import com.manjul.aikeyboard.core.translate.mlkit.MlKitLanguages
import com.manjul.aikeyboard.core.translate.mlkit.ModelUiState
import com.manjul.aikeyboard.core.translate.mlkit.RemoteModels
import com.manjul.aikeyboard.core.translate.mlkit.TranslateModelStore
import com.manjul.aikeyboard.core.voice.whisper.ModelState
import com.manjul.aikeyboard.core.voice.whisper.WhisperModelSpec
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** ML Kit's downloaded set, in memory; the view model tests never download. */
private class PresentModels(private val present: Set<Lang>) : RemoteModels {
    override suspend fun downloaded() = present
    override suspend fun download(lang: Lang, wifiOnly: Boolean) = error("no downloads in these tests")
    override suspend fun delete(lang: Lang) = error("no deletes in these tests")
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class OfflineLanguagesViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val settings = FeatureSettingsStore(RuntimeEnvironment.getApplication(), dispatcher)
    private val whisper = MutableStateFlow(mapOf(WhisperModelSpec.BASE to ModelState.ABSENT))

    @After
    fun tearDown() {
        scope.cancel()
    }

    private suspend fun vm(present: Set<Lang> = emptySet(), onDeviceRecognizer: Boolean = true): OfflineLanguagesViewModel {
        val store = TranslateModelStore(
            models = PresentModels(present),
            workManager = { error("no WorkManager in these tests") },
            settings = settings,
            analytics = {},
            scope = scope,
        )
        store.downloaded() // fills the states, like refresh() without the WorkManager part
        return OfflineLanguagesViewModel(
            store = store,
            settings = settings,
            whisperStates = whisper,
            whisperOffered = listOf(WhisperModelSpec.BASE),
            onDeviceRecognizer = onDeviceRecognizer,
            scope = scope,
            displayLocale = Locale.ENGLISH,
        )
    }

    @Test
    fun mvpLanguagesAndBengaliComeFirstThenTheRestOfTheCatalog() = runTest {
        val tags = vm().state.value.languages.map { it.lang.tag }
        assertEquals(listOf("en", "es", "fr", "pt", "ar", "ur", "id", "hi", "bn"), tags.take(9))
        assertEquals(MlKitLanguages.all.size + 1, tags.size) // the catalog plus cloud-only Pashto
        assertEquals(tags.size, tags.distinct().size)
        val rest = tags.drop(10).map { Locale.forLanguageTag(it).getDisplayName(Locale.ENGLISH).lowercase() }
        assertEquals(rest.sorted(), rest)
    }

    @Test
    fun pashtoIsCloudOnly() = runTest {
        val rows = vm().state.value.languages
        val ps = rows.single { it.lang == Lang("ps") }
        assertTrue(ps.cloudOnly)
        assertFalse(rows.single { it.lang == Lang("ur") }.cloudOnly)
    }

    @Test
    fun storageSummaryCountsReadyModels() = runTest {
        val state = vm(present = setOf(Lang.English, Lang("ur"))).state.value
        assertEquals(2 * ModelSizes.APPROX_MODEL_BYTES, state.storageBytes)
        assertEquals(ModelUiState.READY, state.languages.single { it.lang == Lang("ur") }.state)
        assertEquals(ModelUiState.ABSENT, state.languages.single { it.lang == Lang("hi") }.state)
    }

    @Test
    fun englishPivotIsNotDeletableWhileAnotherLanguageIsReady() = runTest {
        val rows = vm(present = setOf(Lang.English, Lang("ur"))).state.value.languages
        assertFalse(rows.single { it.lang == Lang.English }.deletable)
        assertTrue(rows.single { it.lang == Lang("ur") }.deletable)
        assertTrue(vm(present = setOf(Lang.English)).state.value.languages.single { it.lang == Lang.English }.deletable)
    }

    @Test
    fun whisperRowsMirrorTheModelManagerStates() = runTest {
        val vm = vm()
        assertEquals(listOf(WhisperModelSpec.BASE to ModelState.ABSENT), vm.state.value.whisper)
        whisper.value = mapOf(WhisperModelSpec.BASE to ModelState.DOWNLOADING)
        assertEquals(listOf(WhisperModelSpec.BASE to ModelState.DOWNLOADING), vm.state.value.whisper)
    }

    @Test
    fun recognizerRowOpensSystemVoiceInputSettings() = runTest {
        assertTrue(vm(onDeviceRecognizer = true).state.value.onDeviceRecognizer)
        assertEquals(Settings.ACTION_VOICE_INPUT_SETTINGS, OfflineLanguagesViewModel.recognizerSettingsIntent().action)
    }

    @Test
    fun wifiOnlySwitchCoversTranslationAndWhisper() = runTest {
        val vm = vm()
        assertTrue(vm.state.value.wifiOnly)
        vm.setWifiOnly(false)
        val s = settings.current
        assertFalse(s.translateWifiOnly)
        assertFalse(s.whisperWifiOnly)
        assertFalse(vm.state.value.wifiOnly)
    }
}
