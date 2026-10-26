/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.privacy

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.analytics.ConsentKind
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.datastore.FeatureSettings
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
class PrivacyControlsTest {
    @get:Rule(order = 0)
    val host = ComposeHostActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private var settings by mutableStateOf(FeatureSettings())
    private var withdrawn = 0

    private fun show() = compose.setContent {
        ProvideLocalizedResources(LocalContext.current, appName = R.string.app_name) {
            LazyColumn {
                privacyControls(
                    settings = settings,
                    onUpdate = { settings = it(settings) },
                    onWithdrawAiConsent = {
                        withdrawn++
                        settings = settings.withAiConsentWithdrawn()
                    },
                )
            }
        }
    }

    @Test fun shareAnalyticsIsTheInverseOfOptOut() {
        show()
        compose.onNodeWithText("Share usage analytics").assertIsOn().performClick()
        compose.waitForIdle()
        assertTrue(settings.analyticsOptOut)
        compose.onNodeWithText("Share usage analytics").assertIsOff().performClick()
        compose.waitForIdle()
        assertFalse(settings.analyticsOptOut)
    }

    @Test fun withdrawIsDisabledWithoutConsent() {
        show()
        compose.onNodeWithText("Withdraw AI consent").assertIsNotEnabled()
        compose.onNodeWithText("Withdraw AI consent").performClick()
        compose.waitForIdle()
        assertEquals(0, withdrawn)
    }

    @Test fun withdrawAsksFirstThenClearsConsentAndAutoPolish() {
        settings = FeatureSettings(consentAiAt = 10L, consentAutoPolishAt = 20L, autoPolish = true)
        show()
        compose.onNodeWithText("Withdraw AI consent").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        assertEquals(0, withdrawn)
        assertEquals(10L, settings.consentAiAt)

        compose.onNodeWithText("Withdraw AI consent").performClick()
        compose.onNodeWithText("Withdraw").performClick()
        compose.waitForIdle()
        assertEquals(1, withdrawn)
        assertEquals(0L, settings.consentAiAt)
        assertEquals(0L, settings.consentAutoPolishAt)
        assertFalse(settings.autoPolish)
        assertTrue(compose.onAllNodesWithText("Withdraw").fetchSemanticsNodes().isEmpty())
    }

    @Test fun withdrawalEventsNameOnlyTheConsentsThatWereGiven() {
        assertEquals(
            listOf(EventCatalog.consentWithdrawn(ConsentKind.AI), EventCatalog.consentWithdrawn(ConsentKind.AUTO_POLISH)),
            consentWithdrawnEvents(FeatureSettings(consentAiAt = 1L, consentAutoPolishAt = 2L)),
        )
        assertEquals(
            listOf(EventCatalog.consentWithdrawn(ConsentKind.AI)),
            consentWithdrawnEvents(FeatureSettings(consentAiAt = 1L)),
        )
        assertEquals(emptyList(), consentWithdrawnEvents(FeatureSettings()))
    }
}
