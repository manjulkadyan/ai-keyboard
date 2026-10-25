/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import com.manjul.aikeyboard.BuildConfig
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.datastore.FeatureSettings
import com.manjul.aikeyboard.core.datastore.VoiceEnginePreference
import com.manjul.aikeyboard.core.voice.whisper.ModelState
import com.manjul.aikeyboard.core.voice.whisper.WhisperLanguageGate
import com.manjul.aikeyboard.core.voice.whisper.WhisperModelSpec
import com.manjul.aikeyboard.fork.testing.ComposeHostActivityRule
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class VoiceSettingsTest {
    @get:Rule(order = 0)
    val host = ComposeHostActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private var settings by mutableStateOf(FeatureSettings())
    private val downloads = mutableListOf<Pair<WhisperModelSpec, Boolean>>()
    private var offlineLanguagesOpened = 0

    private fun show(
        whisperOffered: Boolean = true,
        models: Map<WhisperModelSpec, ModelState> = mapOf(WhisperModelSpec.BASE to ModelState.ABSENT),
    ) = compose.setContent {
        ProvideLocalizedResources(LocalContext.current, appName = R.string.app_name) {
            VoiceSettingsContent(
                settings = settings,
                onUpdate = { settings = it(settings) },
                whisperOffered = whisperOffered,
                models = models,
                onDownload = { downloads += it to settings.whisperWifiOnly },
                onDelete = {},
                onOpenOfflineLanguages = { offlineLanguagesOpened++ },
            )
        }
    }

    @Test fun sliderValuesMapToTwoToFiveSecondsOnly() {
        assertEquals(2000, silenceMsFromSlider(-10_000f))
        assertEquals(2000, silenceMsFromSlider(2000f))
        assertEquals(2500, silenceMsFromSlider(2600f))
        assertEquals(5000, silenceMsFromSlider(5000f))
        assertEquals(5000, silenceMsFromSlider(99_000f))
    }

    @Test fun theSliderWritesOnlyTwoThousandToFiveThousand() {
        show()
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(2000f, 2000f..5000f, steps = 5))).assertExists()
        val slider = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(9_999f) }
        compose.waitForIdle()
        assertEquals(5000, settings.voiceSilenceMs)
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(100f) }
        compose.waitForIdle()
        assertEquals(2000, settings.voiceSilenceMs)
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(3_400f) }
        compose.waitForIdle()
        assertEquals(3500, settings.voiceSilenceMs)
    }

    @Test fun pickingOfflineWithoutAModelShowsTheDownloadRow() {
        show()
        assertTrue(compose.onAllNodesWithText(NEEDS_MODEL).isEmpty())
        compose.onNodeWithText("Offline (Whisper)").performClick()
        compose.waitForIdle()
        assertEquals(VoiceEnginePreference.WHISPER, settings.voiceEnginePreference)
        compose.onNodeWithText(NEEDS_MODEL).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Download Base").performScrollTo().performClick()
        assertEquals(listOf(WhisperModelSpec.BASE to true), downloads)
    }

    @Test fun aReadyModelNeedsNoDownloadRow() {
        settings = FeatureSettings(voiceEnginePreference = VoiceEnginePreference.WHISPER)
        show(models = mapOf(WhisperModelSpec.BASE to ModelState.READY))
        assertTrue(compose.onAllNodesWithText(NEEDS_MODEL).isEmpty())
    }

    @Test fun wifiOnlyIsOnByDefaultAndCanBeTurnedOff() {
        assertTrue(FeatureSettings().whisperWifiOnly)
        show()
        compose.onNodeWithText("Download on Wi-Fi only").performScrollTo().performClick()
        compose.waitForIdle()
        assertFalse(settings.whisperWifiOnly)
    }

    @Test fun withNoWhisperLanguageTheOfflineOptionAndModelsAreHidden() {
        show(whisperOffered = false)
        compose.onNodeWithText("Auto").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Offline (Whisper)").isEmpty())
        assertTrue(compose.onAllNodesWithText("Offline models").isEmpty())
    }

    @Test fun offlineLanguagesLinkOpensTheScreen() {
        show()
        compose.onNodeWithText("Offline languages").performScrollTo().performClick()
        assertEquals(1, offlineLanguagesOpened)
    }

    @Test fun offlineLanguagesLinkShowsWithoutWhisperToo() {
        show(whisperOffered = false)
        compose.onNodeWithText("Offline languages").performScrollTo().performClick()
        assertEquals(1, offlineLanguagesOpened)
    }

    @Test fun spokenPunctuationCopyKeepsItsQuotes() {
        show()
        compose.onNodeWithText("Say \"comma\" or \"full stop\" to type the symbol.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test fun onlyAutoMentionsThatVoiceCanLeaveThePhone() {
        show()
        compose.onNodeWithText("processes your voice off the phone", substring = true).assertIsDisplayed()
        assertEquals(2, compose.onAllNodesWithText("Your voice stays on this phone", substring = true).size)
    }

    @Test fun releaseHidesWhisperForEveryLanguageAndDebugAndBetaShowAll() {
        val release = WhisperLanguageGate.parse("")
        for (lang in listOf("en-US", "hi-IN", "ur", "ar", "pt-BR")) assertFalse(release.enabled(lang), lang)
        assertFalse(release.anyEnabled)

        // Debug and beta enable every language; release (and benchmark, built from it) none.
        val built = WhisperLanguageGate.parse(BuildConfig.WHISPER_ENABLED_LANGS)
        val all = BuildConfig.BUILD_TYPE == "debug" || BuildConfig.BUILD_TYPE == "beta"
        for (lang in listOf("en-US", "hi-IN", "ur", "ar", "pt-BR")) assertEquals(all, built.enabled(lang), lang)
        assertEquals(all, built.anyEnabled)

        val some = WhisperLanguageGate.parse("en, hi")
        assertTrue(some.enabled("en-US") && some.enabled("hi-IN"))
        assertFalse(some.enabled("ur"))
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithText(
        t: String,
        substring: Boolean = false,
    ) = onAllNodes(androidx.compose.ui.test.hasText(t, substring = substring)).fetchSemanticsNodes()

    private companion object {
        const val NEEDS_MODEL =
            "Download a model below. Until then, on-device speech recognition is used where the phone has it."
    }
}
