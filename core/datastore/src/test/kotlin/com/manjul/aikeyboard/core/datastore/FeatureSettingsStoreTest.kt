/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.datastore

import android.app.Application
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
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
class FeatureSettingsStoreTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()

    @Test
    fun `defaults`() = runTest {
        val s = FeatureSettingsStore(app).flow.first()
        assertEquals(FeatureSettings(), s)
        assertEquals(2000, s.voiceSilenceMs)
        assertTrue(s.spokenPunctuation)
        assertNull(s.translatePair)
        assertEquals(BarMode.PREVIEW, s.barMode)
        assertFalse(s.barLiveComposingSet)
        assertTrue(s.translateWifiOnly)
        assertFalse(s.cloudTranslateOptIn)
        assertTrue(s.translateHistoryEnabled)
        assertEquals(0L, s.firstTranslateAt)
        assertNull(s.lastSourceLang)
        assertEquals(0L, s.enableKeyboardCardShownAt)
        assertEquals(emptySet(), s.translateStopped)
    }

    @Test
    fun `update round-trips`() = runTest {
        val expected = FeatureSettings(
            incognitoManual = true,
            privatePackages = setOf("a.b"),
            voiceEnginePreference = VoiceEnginePreference.WHISPER,
            translatePair = LanguagePair("en", "de"),
            onboardingStep = 3,
            firstDictationAt = 42L,
        )
        val store = FeatureSettingsStore(app)
        store.update { expected }
        assertEquals(expected, store.flow.first())
        assertEquals(expected, store.current)
        // What is on disk decodes to the same value.
        val file = File(app.filesDir, "datastore/feature_settings.json")
        assertEquals(expected, file.inputStream().use { FeatureSettingsSerializer.readFrom(it) })
    }

    @Test
    fun `translate fields round-trip`() = runTest {
        val expected = FeatureSettings(
            translatePair = LanguagePair("ur", "en"),
            barMode = BarMode.LIVE,
            barLiveComposingSet = true,
            translateWifiOnly = false,
            cloudTranslateOptIn = true,
            translateHistoryEnabled = false,
            firstTranslateAt = 1_700_000_000_000L,
            lastSourceLang = "hi",
            enableKeyboardCardShownAt = 1_700_000_000_001L,
            translateStopped = setOf("ur", "bn"),
        )
        val store = FeatureSettingsStore(app)
        store.update { expected }
        assertEquals(expected, store.flow.first())
        val file = File(app.filesDir, "datastore/feature_settings.json")
        assertEquals(expected, file.inputStream().use { FeatureSettingsSerializer.readFrom(it) })
    }

    @Test
    fun `sprint 1 files without the translate keys load with defaults`() = runTest {
        val old = """{"onboardingDone":true,"translatePair":{"source":"en","target":"ur"},"whisperWifiOnly":false}"""
        val decoded = FeatureSettingsSerializer.readFrom(old.byteInputStream())
        assertEquals(
            FeatureSettings(onboardingDone = true, translatePair = LanguagePair("en", "ur"), whisperWifiOnly = false),
            decoded,
        )
    }

    @Test
    fun `translate fields are clamped on write`() = runTest {
        val store = FeatureSettingsStore(app)
        store.update {
            it.copy(firstTranslateAt = -5L, enableKeyboardCardShownAt = -1L, lastSourceLang = "  ")
        }
        val s = store.flow.first()
        assertEquals(0L, s.firstTranslateAt)
        assertEquals(0L, s.enableKeyboardCardShownAt)
        assertNull(s.lastSourceLang)
    }

    @Test
    fun `files with the removed smartbar keys still decode`() = runTest {
        // smartbarPinned/smartbarOrder were written by earlier builds; the smartbar layout lives in upstream prefs.
        val old = """{"incognitoManual":true,"smartbarPinned":["EMOJI"],"smartbarOrder":["MIC","EMOJI"]}"""
        val decoded = FeatureSettingsSerializer.readFrom(old.byteInputStream())
        assertEquals(FeatureSettings(incognitoManual = true), decoded)
    }

    @Test
    fun `voiceSilenceMs is clamped on write`() = runTest {
        val store = FeatureSettingsStore(app)
        store.update { it.copy(voiceSilenceMs = 9000) }
        assertEquals(5000, store.flow.first().voiceSilenceMs)
        store.update { it.copy(voiceSilenceMs = 10) }
        assertEquals(2000, store.flow.first().voiceSilenceMs)
    }

    @Test
    fun `corrupt file resets to defaults`() = runTest {
        val file = File(app.filesDir, "datastore/feature_settings.json")
        file.parentFile!!.mkdirs()
        file.writeBytes(byteArrayOf(0x00, 0x7B, 0x7F, 0x13, 0x42))

        val store = FeatureSettingsStore(app)

        assertEquals(FeatureSettings(), store.flow.first())
        store.update { it.copy(onboardingStep = 2) }
        assertEquals(2, store.flow.first().onboardingStep)
    }
}
