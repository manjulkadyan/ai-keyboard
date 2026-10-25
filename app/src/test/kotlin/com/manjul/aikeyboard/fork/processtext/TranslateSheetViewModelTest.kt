/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.processtext

import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.ProcessTextAction
import com.manjul.aikeyboard.core.database.TranslationHistoryEntry
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.core.datastore.LanguagePair
import com.manjul.aikeyboard.core.translate.EngineId
import com.manjul.aikeyboard.core.translate.FailKind
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.translate.LangPair
import com.manjul.aikeyboard.core.translate.Origin
import com.manjul.aikeyboard.core.translate.RouterContext
import com.manjul.aikeyboard.core.translate.TranslateRequest
import com.manjul.aikeyboard.core.translate.TranslateResult
import com.manjul.aikeyboard.core.translate.mlkit.ModelUiState
import com.manjul.aikeyboard.core.tts.TtsAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class FakeSheetModels : SheetModels {
    override val states = MutableStateFlow<Map<Lang, ModelUiState>>(emptyMap())
    val downloads = mutableListOf<LangPair>()

    override suspend fun refresh() = Unit

    override fun download(pair: LangPair) {
        downloads += pair
    }
}

internal class FakeSpeaker(var availability: TtsAvailability = TtsAvailability.AVAILABLE) : SheetSpeaker {
    val spoken = mutableListOf<Pair<String, Lang>>()
    var released = false

    override suspend fun availability(lang: Lang) = availability

    override fun speak(text: String, lang: Lang) {
        spoken += text to lang
    }

    override fun stop() = Unit

    override fun release() {
        released = true
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TranslateSheetViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val store = FeatureSettingsStore(RuntimeEnvironment.getApplication(), dispatcher)
    private val events = mutableListOf<AnalyticsEvent>()
    private val history = mutableListOf<TranslationHistoryEntry>()
    private val requests = mutableListOf<Pair<TranslateRequest, RouterContext>>()
    private val models = FakeSheetModels()
    private val speaker = FakeSpeaker()
    private var now = 1_000_000_000L
    private var detected = Lang("es")
    private var result: (TranslateRequest) -> TranslateResult = { TranslateResult.Ok("T(${it.text})", it.source!!, EngineId.MLKIT) }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun input(text: String = "hola amigo", action: ProcessTextAction = ProcessTextAction.PROCESS_TEXT, readOnly: Boolean = false) =
        ProcessTextInput(text, action, readOnly, truncated = false)

    private fun vm(input: ProcessTextInput = input(), imeEnabled: Boolean = true) = TranslateSheetViewModel(
        input,
        TranslateSheetViewModel.Deps(
            store = store,
            translate = { req, ctx, _ ->
                requests += req to ctx
                result(req)
            },
            detect = { _, _ -> detected },
            models = models,
            speaker = speaker,
            history = { history += it },
            imeEnabled = flowOf(imeEnabled),
            analytics = { events += it },
            clock = { now },
        ),
        scope,
    )

    private fun names() = events.map { it.name }

    @Test
    fun translatesOnOpenFromTheDetectedSourceToThePairTarget() = runTest {
        store.update { it.copy(translatePair = LanguagePair("en", "fr")) }
        val vm = vm()
        val s = vm.state.value
        assertEquals("T(hola amigo)", s.translation)
        assertNull(s.status)
        assertEquals(LangPair(Lang("es"), Lang("fr")), s.pair)
        val (req, ctx) = requests.single()
        assertEquals(Origin.PROCESS_TEXT, req.origin)
        assertTrue(ctx.allowTranslate)
        assertFalse(ctx.pro)
        assertEquals(listOf("translate_done", "first_translate"), names())
        assertEquals("process_text", events[0].params["surface"])
        assertEquals("es", store.flow.first().lastSourceLang)
        assertTrue(store.flow.first().firstTranslateAt > 0)
    }

    @Test
    fun textAlreadyInTheTargetGoesToThePairSource() = runTest {
        store.update { it.copy(translatePair = LanguagePair("ur", "en")) }
        detected = Lang.English
        val vm = vm(input("good morning"))
        assertEquals(LangPair(Lang.English, Lang("ur")), vm.state.value.pair)
    }

    @Test
    fun firstTranslateIsLoggedOnlyOnceEver() = runTest {
        vm()
        vm()
        assertEquals(1, names().count { it == "first_translate" })
        assertEquals(2, names().count { it == "translate_done" })
    }

    @Test
    fun replaceReturnsTheTranslationLogsAndSavesHistoryOnce() = runTest {
        val vm = vm()
        assertEquals("T(hola amigo)", vm.onReplace())
        vm.onCopy()
        assertEquals(1, names().count { it == "process_text_replace" })
        val entry = history.single()
        assertEquals("process_text", entry.origin)
        assertEquals("mlkit", entry.engine)
        assertEquals("hola amigo", entry.sourceText)
    }

    @Test
    fun replaceIsRefusedForReadOnlyAndShare() = runTest {
        assertNull(vm(input(readOnly = true)).onReplace())
        assertNull(vm(input(action = ProcessTextAction.SEND, readOnly = true)).onReplace())
        assertTrue(names().none { it == "process_text_replace" })
        assertTrue(history.isEmpty())
    }

    @Test
    fun historyIsNotSavedJustForOpeningOrWhenTurnedOff() = runTest {
        vm()
        assertTrue(history.isEmpty())
        store.update { it.copy(translateHistoryEnabled = false) }
        vm().onCopy()
        vm().onOpenInApp()
        assertTrue(history.isEmpty())
    }

    @Test
    fun openInAppReturnsTheOriginalTextAndSavesHistory() = runTest {
        assertEquals("hola amigo", vm().onOpenInApp())
        assertEquals(1, history.size)
    }

    @Test
    fun missingPackOffersADownloadAndRetriesWhenReady() = runTest {
        store.update { it.copy(translatePair = LanguagePair("es", "en")) }
        var ready = false
        result = { if (ready) TranslateResult.Ok("hello friend", Lang("es"), EngineId.MLKIT) else TranslateResult.Failed(FailKind.NO_PACK) }
        val vm = vm()
        val status = vm.state.value.status
        assertTrue(status is SheetStatus.NeedsDownload && status.bytes > 0, "$status")
        assertEquals(listOf("translate_error"), names())
        assertEquals("no_pack", events[0].params["reason"])
        vm.download()
        assertEquals(listOf(LangPair(Lang("es"), Lang.English)), models.downloads)
        assertEquals(SheetStatus.Downloading, vm.state.value.status)
        ready = true
        models.states.value = mapOf(Lang("es") to ModelUiState.READY, Lang.English to ModelUiState.READY)
        assertEquals("hello friend", vm.state.value.translation)
        assertNull(vm.state.value.status)
    }

    @Test
    fun failedDownloadOffersDownloadAgain() = runTest {
        store.update { it.copy(translatePair = LanguagePair("es", "en")) }
        result = { TranslateResult.Failed(FailKind.NO_PACK) }
        val vm = vm()
        vm.download()
        models.states.value = mapOf(Lang("es") to ModelUiState.FAILED)
        assertTrue(vm.state.value.status is SheetStatus.NeedsDownload)
    }

    @Test
    fun errorsMapToStatusesAndRetryTranslatesAgain() = runTest {
        result = { TranslateResult.Failed(FailKind.OFFLINE) }
        val vm = vm()
        assertEquals(SheetStatus.Error(FailKind.OFFLINE), vm.state.value.status)
        assertNull(vm.onCopy())
        result = { TranslateResult.Failed(FailKind.CLOUD_UNAVAILABLE) }
        vm.translate()
        assertEquals(SheetStatus.CloudOnly, vm.state.value.status)
        result = { error("engine blew up with the user's text") }
        vm.translate()
        assertEquals(SheetStatus.Error(FailKind.ENGINE), vm.state.value.status)
        assertEquals(3, requests.size)
    }

    @Test
    fun pickingATargetUpdatesTheSharedPairAndTranslatesAgain() = runTest {
        val vm = vm()
        vm.pickTarget(Lang("de"))
        assertEquals(Lang("de"), vm.state.value.pair.target)
        assertEquals(Lang("de"), requests.last().first.target)
        assertEquals(LanguagePair("es", "de"), store.flow.first().translatePair)
    }

    @Test
    fun enableKeyboardCardShowsWhenNotEnabledAtMostOncePer7Days() = runTest {
        assertTrue(vm(imeEnabled = false).state.value.showEnableKeyboard)
        assertEquals(now, store.flow.first().enableKeyboardCardShownAt)
        now += TranslateSheetViewModel.CARD_INTERVAL_MS - 1
        assertFalse(vm(imeEnabled = false).state.value.showEnableKeyboard)
        now += 1
        assertTrue(vm(imeEnabled = false).state.value.showEnableKeyboard)
        assertFalse(vm(imeEnabled = true).state.value.showEnableKeyboard)
    }

    @Test
    fun speakUsesTheTargetVoiceOrAsksForItsInstall() = runTest {
        val vm = vm()
        assertEquals(SpeakOutcome.SPOKEN, vm.speak())
        assertEquals("T(hola amigo)" to vm.state.value.pair.target, speaker.spoken.single())
        speaker.availability = TtsAvailability.MISSING_DATA
        assertEquals(SpeakOutcome.MISSING_VOICE, vm.speak())
        speaker.availability = TtsAvailability.NOT_SUPPORTED
        assertEquals(SpeakOutcome.UNSUPPORTED, vm.speak())
        assertTrue(vm.state.value.speakUnavailable)
        vm.clear()
        assertTrue(speaker.released)
    }

    @Test
    fun openEventCarriesActionAndEditable() = runTest {
        vm(input(readOnly = true)).onOpened()
        vm(input(action = ProcessTextAction.SEND, readOnly = true)).onOpened()
        val opens = events.filter { it.name == "process_text_open" }
        assertEquals(listOf("process_text" to false, "send" to false), opens.map { it.params["action"] to it.params["editable"] })
    }

    @Test
    fun analyticsAndStateNeverCarryTheText() = runTest {
        val vm = vm(input("my secret phrase"))
        vm.onReplace()
        assertFalse(vm.state.value.toString().contains("secret"))
        for (event in events) assertFalse(event.toString().contains("secret"), event.toString())
    }
}
