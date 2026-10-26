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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.datastore.FeatureSettings
import com.manjul.aikeyboard.fork.testing.ComposeHostActivityRule
import java.util.Locale
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
class TypingSettingsTest {
    @get:Rule(order = 0)
    val host = ComposeHostActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private var settings by mutableStateOf(FeatureSettings())
    private var cleared = 0

    private fun show() = compose.setContent {
        ProvideLocalizedResources(LocalContext.current, appName = R.string.app_name) {
            TypingSettingsContent(
                settings = settings,
                onUpdate = { settings = it(settings) },
                onClearLearned = { cleared++ },
            )
        }
    }

    @Test fun languageNamesComeFromTheLocale() {
        assertEquals("English", languageName("en", Locale.ENGLISH))
        assertEquals("Hindi", languageName("hi", Locale.ENGLISH))
        assertEquals("Indonesian", languageName("id", Locale.ENGLISH))
    }

    @Test fun suggestionsToggleWrites() {
        show()
        compose.onNodeWithText("Suggestions").performClick()
        compose.waitForIdle()
        assertFalse(settings.suggestionsEnabled)
    }

    @Test fun autocorrectIsPerLanguage() {
        show()
        val hindi = languageName("hi")
        compose.onNodeWithText(hindi).performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue("hi" in settings.autocorrectLocales)
        compose.onNodeWithText(languageName("en")).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(setOf("es", "fr", "pt", "id", "hi"), settings.autocorrectLocales)
    }

    @Test fun clearAsksFirstAndCancelKeepsTheWords() {
        show()
        compose.onNodeWithText("Clear learned words").performScrollTo().performClick()
        compose.onNodeWithText("Delete every word the keyboard has learned on this phone?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        assertEquals(0, cleared)
        assertTrue(compose.onAllNodesWithText("Delete every word the keyboard has learned on this phone?")
            .fetchSemanticsNodes().isEmpty())

        compose.onNodeWithText("Clear learned words").performScrollTo().performClick()
        compose.onNodeWithText("Clear").performClick()
        compose.waitForIdle()
        assertEquals(1, cleared)
    }
}
