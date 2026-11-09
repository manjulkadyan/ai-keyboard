/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.datastore.BarMode
import com.manjul.aikeyboard.core.datastore.FeatureSettings
import com.manjul.aikeyboard.core.datastore.LanguagePair
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
class TranslateSettingsTest {
    @get:Rule(order = 0)
    val host = ComposeHostActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private var settings by mutableStateOf(FeatureSettings())
    private var offlinePacks = 0

    private fun show() = compose.setContent {
        ProvideLocalizedResources(LocalContext.current, appName = R.string.app_name) {
            TranslateSettingsContent(
                settings = settings,
                onUpdate = { settings = it(settings) },
                onOfflinePacks = { offlinePacks++ },
            )
        }
    }

    @Test fun theLiveSwitchWritesTheBarModeAndMarksItAsSet() {
        show()
        compose.onNodeWithText("Translate while typing").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(BarMode.LIVE, settings.barMode)
        assertTrue(settings.barLiveComposingSet)
        compose.onNodeWithText("Translate while typing").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(BarMode.PREVIEW, settings.barMode)
        assertTrue(settings.barLiveComposingSet)
    }

    @Test fun wifiOnlyAndHistoryAreSwitches() {
        show()
        compose.onNodeWithText("Download packs on Wi-Fi only").performScrollTo().performClick()
        compose.onNodeWithText("Save translation history").performScrollTo().performClick()
        compose.waitForIdle()
        assertFalse(settings.translateWifiOnly)
        assertFalse(settings.translateHistoryEnabled)
    }

    @Test fun theDefaultPairIsPickedFromTheMvpLanguages() {
        settings = settings.copy(translatePair = LanguagePair("en", "es"))
        show()
        compose.onNodeWithText("Translate to").performScrollTo().performClick()
        compose.onNodeWithText("Hindi").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(LanguagePair("en", "hi"), settings.translatePair)
    }

    @Test fun offlinePacksOpenTheExistingScreenAndThereIsNoCloudRow() {
        show()
        compose.onNodeWithText("Offline languages").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(1, offlinePacks)
        compose.onNodeWithText("Cloud", substring = true).assertDoesNotExist()
    }
}
