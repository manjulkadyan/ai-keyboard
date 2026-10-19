/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.onboarding

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.fork.testing.ComposeHostActivityRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class OnboardingScreenTest {
    @get:Rule(order = 0)
    val host = ComposeHostActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val store = FeatureSettingsStore(RuntimeEnvironment.getApplication())
    private var micRequests = 0

    private fun show(micGranted: Boolean? = null) = compose.setContent {
        ProvideLocalizedResources(LocalContext.current, appName = R.string.app_name) {
        TryItStep(
            engineKind = { DisclosureEngineKind.ON_DEVICE },
            store = store,
            clock = { 777L },
            micGranted = micGranted,
            requestMicPermission = { micRequests++ },
            onSkip = {},
            onDone = {},
        )
        }
    }

    @Test
    fun tryItShowsTextFieldAndSkip() {
        show()
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        compose.onNodeWithText("Skip").assertIsDisplayed()
    }

    @Test
    fun micPermissionIsRequestedOnlyAfterAgree() {
        show()
        compose.onNodeWithText("Speak instead").performClick()
        compose.onNodeWithText("Agree").assertIsDisplayed()
        assertEquals(0, micRequests)
        compose.onNodeWithText("Agree").performClick()
        compose.waitUntil(5_000) { micRequests == 1 }
        assertEquals(1, micRequests)
        val consent = runBlocking { store.flow.first { it.consentVoiceNoticeAt != 0L }.consentVoiceNoticeAt }
        assertEquals(777L, consent)
        assertTrue(compose.onAllNodesWithTextCount("Agree") == 0)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(t: String) =
        onAllNodesWithText(t).fetchSemanticsNodes().size
}
