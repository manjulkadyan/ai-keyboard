/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translator

import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.datastore.FeatureSettings
import com.manjul.aikeyboard.core.datastore.LanguagePair
import com.manjul.aikeyboard.core.translate.EngineId
import com.manjul.aikeyboard.core.translate.FailKind
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.translate.LangPair
import com.manjul.aikeyboard.core.translate.TranslateResult
import com.manjul.aikeyboard.core.translate.bar.BarStatus
import com.manjul.aikeyboard.core.tts.TtsAvailability
import java.io.File
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TranslatorViewModelTest {
    private fun TestScope.rig(
        settings: FeatureSettings = FeatureSettings(),
        results: MutableList<TranslateResult> = mutableListOf(),
    ): TranslatorRig = TranslatorRig(backgroundScope, settings, results).also { runCurrent() }

    /** Types [text] and lets the 600 ms debounce and the call finish. */
    private fun TestScope.type(r: TranslatorRig, text: String) {
        r.vm.onInput(text)
        advanceTimeBy(700)
        runCurrent()
    }

    @Test
    fun inputMakesOneCallAfter600ms() = runTest {
        val r = rig()
        r.vm.onInput("h")
        advanceTimeBy(300)
        r.vm.onInput("he")
        advanceTimeBy(599)
        runCurrent()
        assertEquals(0, r.requests.size)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("he"), r.requests.map { it.text })
        assertEquals("<he>", r.vm.state.value.output)
    }

    @Test
    fun resultIsSavedOncePerSessionAndUpdatedWhileTyping() = runTest {
        val r = rig()
        type(r, "hello")
        assertEquals(1, r.dao.rows.value.size)
        type(r, "hello world")
        val rows = r.dao.rows.value
        assertEquals(1, rows.size)
        assertEquals("hello world", rows[0].sourceText)
        assertEquals("<hello world>", rows[0].targetText)
        assertEquals("en", rows[0].sourceLang)
        assertEquals("es", rows[0].targetLang)
        assertEquals("text", rows[0].origin)

        r.vm.swap()
        // advanceUntilIdle() skips backgroundScope work, where the view model runs.
        runCurrent()
        assertEquals(listOf("hello", "hello world", "<hello world>"), r.requests.map { it.text })
        assertEquals(2, r.dao.rows.value.size)
        assertEquals(LangPair(Lang("es"), Lang.English), r.vm.state.value.pair)
        assertEquals(LanguagePair("es", "en"), r.settings.current.translatePair)
    }

    @Test
    fun clearingStartsANewRow() = runTest {
        val r = rig()
        type(r, "one")
        type(r, "")
        type(r, "two")
        assertEquals(listOf("one", "two"), r.dao.rows.value.map { it.sourceText })
    }

    @Test
    fun historyOffSavesNothing() = runTest {
        val r = rig(settings = FeatureSettings(translateHistoryEnabled = false))
        type(r, "hello")
        assertEquals("<hello>", r.vm.state.value.output)
        assertFalse(r.vm.state.value.historyEnabled)
        assertTrue(r.dao.rows.value.isEmpty())
    }

    @Test
    fun overLimitIsTruncatedTo5000() = runTest {
        val r = rig()
        type(r, "a".repeat(5_001))
        val s = r.vm.state.value
        assertEquals(5_000, s.input.length)
        assertEquals(5_000, s.count)
        assertTrue(s.atLimit)
        assertEquals(5_000, r.requests.single().text.length)
    }

    @Test
    fun noPackOffersDownloadWithSizeAndKeepsPreviousResult() = runTest {
        val r = rig(
            results = mutableListOf(
                TranslateResult.Ok("hola", Lang.English, EngineId.MLKIT),
                TranslateResult.Failed(FailKind.NO_PACK),
            ),
        )
        type(r, "hi")
        type(r, "hi there")
        val s = r.vm.state.value
        val status = s.status
        assertTrue(status is BarStatus.NeedsDownload, "status=$status")
        assertEquals(LangPair(Lang.English, Lang("es")), status.pair)
        assertTrue(status.bytes > 0)
        assertEquals("hola", s.output)
        assertEquals(1, r.named(EventCatalog.TRANSLATE_ERROR).size)
        assertEquals("no_pack", r.named(EventCatalog.TRANSLATE_ERROR).single().params["reason"])

        r.vm.download()
        assertEquals(listOf(LangPair(Lang.English, Lang("es"))), r.models.downloads)
    }

    @Test
    fun firstTranslateIsLoggedOnceAndPersisted() = runTest {
        val r = rig()
        r.now = 42L
        type(r, "a")
        type(r, "ab")
        type(r, "")
        type(r, "c")
        val first = r.named(EventCatalog.FIRST_TRANSLATE)
        assertEquals(1, first.size)
        assertEquals("translator", first[0].params["surface"])
        assertEquals(42L, r.settings.current.firstTranslateAt)

        // A later screen (or the keyboard bar first) already set it: never logged again.
        val again = rig(settings = r.settings.current)
        type(again, "d")
        assertTrue(again.named(EventCatalog.FIRST_TRANSLATE).isEmpty())
    }

    @Test
    fun translateDoneIsLoggedOncePerSession() = runTest {
        val r = rig()
        type(r, "h")
        type(r, "hello")
        assertTrue(r.named(EventCatalog.TRANSLATE_DONE).isEmpty())
        r.vm.onLeave()
        r.vm.onLeave()
        val done = r.named(EventCatalog.TRANSLATE_DONE)
        assertEquals(1, done.size)
        assertEquals("translator", done[0].params["surface"])
        assertTrue("chars_bucket" in done[0].params)

        type(r, "again")
        r.vm.swap()
        assertEquals(2, r.named(EventCatalog.TRANSLATE_DONE).size)
    }

    @Test
    fun speakWithMissingVoiceOffersInstall() = runTest {
        val r = rig()
        r.tts.availability = TtsAvailability.MISSING_DATA
        type(r, "hello")
        assertEquals(SpeakAvailability.MISSING_DATA, r.vm.state.value.speak)
        r.vm.speak()
        runCurrent()
        assertTrue(r.vm.state.value.ttsInstallOffered)
        assertTrue(r.tts.spoken.isEmpty())
    }

    @Test
    fun speakWithVoiceSpeaksTheTranslation() = runTest {
        val r = rig()
        type(r, "hello")
        r.vm.speak()
        runCurrent()
        assertEquals(listOf("<hello>"), r.tts.spoken)
        assertFalse(r.vm.state.value.ttsInstallOffered)
    }

    @Test
    fun micPartialsFillTheInput() = runTest {
        val r = rig()
        type(r, "typed")
        r.vm.startMic()
        runCurrent()
        assertEquals("en", r.mic.lang)
        val onText = assertNotNull(r.mic.onText)
        onText("hel", false)
        assertEquals("typed hel", r.vm.state.value.input)
        onText("hello", true)
        assertEquals("typed hello", r.vm.state.value.input)
        advanceTimeBy(700)
        runCurrent()
        assertEquals("voice", r.dao.rows.value.single().origin)
    }

    @Test
    fun onLeaveStopsTtsAndMic() = runTest {
        val r = rig()
        r.vm.startMic()
        runCurrent()
        val ttsStops = r.tts.stops
        r.vm.onLeave()
        runCurrent()
        assertEquals(ttsStops + 1, r.tts.stops)
        assertEquals(1, r.mic.stops)
        assertEquals(MicState.Idle, r.vm.state.value.mic)
    }

    @Test
    fun favouriteMarksTheSessionRow() = runTest {
        val r = rig()
        type(r, "hello")
        r.vm.toggleFavorite()
        runCurrent()
        assertTrue(r.vm.state.value.favorite)
        assertTrue(r.dao.rows.value.single().isFavorite)
        r.vm.toggleFavorite()
        runCurrent()
        assertFalse(r.dao.rows.value.single().isFavorite)
    }

    @Test
    fun favouriteWithHistoryOffSavesOnlyThatRow() = runTest {
        val r = rig(settings = FeatureSettings(translateHistoryEnabled = false))
        type(r, "hello")
        r.vm.toggleFavorite()
        runCurrent()
        assertTrue(r.dao.rows.value.single().isFavorite)
        r.vm.toggleFavorite()
        runCurrent()
        assertTrue(r.dao.rows.value.isEmpty())
    }

    @Test
    fun autoDetectShowsDetectedLanguageAndRemembersIt() = runTest {
        val r = rig(results = mutableListOf(TranslateResult.Ok("hello", Lang("ur"), EngineId.MLKIT)))
        r.vm.setAutoDetect()
        type(r, "salaam")
        assertNull(r.requests.single().source)
        assertEquals(Lang("ur"), r.vm.state.value.detected)
        assertEquals("ur", r.settings.current.lastSourceLang)
    }

    @Test
    fun pickingTheTargetAsSourceSwaps() = runTest {
        val r = rig()
        r.vm.pickSource(Lang("es"))
        assertEquals(LangPair(Lang("es"), Lang.English), r.vm.state.value.pair)
    }

    @Test
    fun defaultPairFollowsDeviceLanguage() {
        assertEquals(LangPair(Lang.English, Lang("es")), TranslatorViewModel.defaultPairFor(Locale.US))
        assertEquals(LangPair(Lang("ur"), Lang.English), TranslatorViewModel.defaultPairFor(Locale.forLanguageTag("ur-PK")))
    }

    @Test
    fun clampKeepsSurrogatePairsWhole() {
        val text = "a".repeat(4_999) + "😀"
        assertEquals(4_999, TranslatorViewModel.clamp(text).length)
    }

    @Test
    fun translatorPackageHasNoAdsDependency() {
        // FR-12 AC2: no interstitial. Gradle runs unit tests from the module directory.
        val dir = File("src/main/kotlin/com/manjul/aikeyboard/fork/translator")
        val files = dir.listFiles { f -> f.extension == "kt" }.orEmpty()
        assertTrue(files.size >= 7, "found ${files.size} files in ${dir.absolutePath}")
        for (f in files) {
            val imports = f.readLines().filter { it.startsWith("import ") }
            assertTrue(imports.none { "core.ads" in it }, "${f.name} imports core.ads")
        }
    }
}
