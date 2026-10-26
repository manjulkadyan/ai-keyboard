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
import com.manjul.aikeyboard.core.datastore.FeatureSettings
import com.manjul.aikeyboard.fork.testing.ComposeHostActivityRule
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AiSettingsTest {
    @get:Rule(order = 0)
    val host = ComposeHostActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private var settings by mutableStateOf(FeatureSettings())
    private val autoPolish = mutableListOf<Boolean>()
    private var withdrawn = 0

    private fun show(isPro: Boolean = false) = compose.setContent {
        ProvideLocalizedResources(LocalContext.current, appName = R.string.app_name) {
            AiSettingsContent(
                settings = settings,
                isPro = isPro,
                onUpdate = { settings = it(settings) },
                onAutoPolish = { autoPolish += it },
                onWithdrawAiConsent = { withdrawn++ },
            )
        }
    }

    @Test fun outputScriptIsSaved() {
        show()
        compose.onNodeWithText("Roman").performClick()
        compose.waitForIdle()
        assertEquals("ROMAN", settings.polishOutputScript)
        compose.onNodeWithText("Same as dictated").performClick()
        compose.waitForIdle()
        assertEquals("MATCH_INPUT", settings.polishOutputScript)
    }

    @Test fun autoPolishIsDisabledWithoutPro() {
        show(isPro = false)
        compose.onNodeWithText("Auto-polish every dictation").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(autoPolish.isEmpty())
    }

    @Test fun autoPolishChangeNeedsProAndItsOwnConsent() {
        val none = FeatureSettings()
        assertEquals(AutoPolishChange.NONE, autoPolishChange(none, on = true, isPro = false))
        assertEquals(AutoPolishChange.LAUNCH_CONSENT, autoPolishChange(none, on = true, isPro = true))
        val agreed = none.copy(consentAutoPolishAt = 5L)
        assertEquals(AutoPolishChange.TURN_ON, autoPolishChange(agreed, on = true, isPro = true))
        assertEquals(AutoPolishChange.TURN_OFF, autoPolishChange(agreed.copy(autoPolish = true), on = false, isPro = false))
        assertEquals(AutoPolishChange.NONE, autoPolishChange(agreed, on = false, isPro = true))
    }

    @Test fun withdrawAsksFirst() {
        settings = settings.copy(consentAiAt = 1L)
        show()
        compose.onNodeWithText("Withdraw AI consent").performScrollTo().performClick()
        compose.onNodeWithText("Withdraw").performClick()
        compose.waitForIdle()
        assertEquals(1, withdrawn)
    }
}
