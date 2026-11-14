/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.dictionary

import android.app.Activity
import android.content.Intent
import android.os.Looper
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import com.manjul.aikeyboard.core.content.dictionary.Attribution
import com.manjul.aikeyboard.core.content.dictionary.Definition
import com.manjul.aikeyboard.core.content.dictionary.LookupResult
import com.manjul.aikeyboard.core.tts.TtsAvailability
import com.manjul.aikeyboard.fork.routes.RouteRegistry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DefineTextActivityTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = RuntimeEnvironment.getApplication()
    private val lookups = mutableListOf<String>()
    private val result = LookupResult(
        definitions = listOf(Definition("airport", "noun", "A place where planes land.", null, emptyList())),
        offline = true,
        attribution = Attribution("Wiktionary", "Wiktionary contributors", "CC BY-SA 4.0", "https://example.org/l"),
    )

    @Before
    fun setUp() {
        DefineTextActivity.depsOverride = DefineTextActivity.Deps(
            lookup = { word ->
                lookups += word
                result
            },
            speak = { TtsAvailability.AVAILABLE },
            stopSpeaking = {},
            installVoiceIntent = { Intent("test.INSTALL_VOICE") },
        )
    }

    @After
    fun tearDown() {
        DefineTextActivity.depsOverride = null
    }

    private fun processText(text: String) =
        Intent(app, DefineTextActivity::class.java)
            .setAction(Intent.ACTION_PROCESS_TEXT)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_PROCESS_TEXT, text)

    private fun launch(intent: Intent): ActivityScenario<DefineTextActivity> {
        val scenario = ActivityScenario.launchActivityForResult<DefineTextActivity>(intent)
        idle()
        return scenario
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    @Test
    fun twoWordsShowTheHintAndLookNothingUp() {
        launch(processText("two words"))
        compose.onNodeWithText("Select one word").assertExists()
        assertTrue(lookups.isEmpty())
        compose.onNodeWithText("Replace").assertDoesNotExist()
    }

    @Test
    fun oneWordShowsTheResultWithoutReplace() {
        val scenario = launch(processText("  airport "))
        assertEquals(listOf("airport"), lookups)
        compose.onNodeWithText("1. A place where planes land.").assertExists()
        compose.onNodeWithText("Licence: CC BY-SA 4.0").assertExists()
        compose.onNodeWithText("Replace").assertDoesNotExist()
        compose.onNodeWithText("Close").performClick()
        idle()
        assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
    }

    @Test
    fun openInAppStartsTheDictionaryRouteWithTheWord() {
        launch(processText("airport"))
        compose.onNodeWithText("Open in app").performClick()
        idle()
        val started = shadowOf(app).nextStartedActivity
        assertEquals(RouteRegistry.DICTIONARY, started.getStringExtra(RouteRegistry.EXTRA_OPEN_SCREEN))
        assertEquals("airport", started.getStringExtra(RouteRegistry.EXTRA_DICTIONARY_WORD))
    }

    @Test
    fun tooLongOrBlankIsNotOneWordAndSendIsRefused() {
        assertNull(DefineTextActivity.oneWord("a".repeat(65)))
        assertNull(DefineTextActivity.oneWord("   "))
        assertNull(DefineTextActivity.oneWord("two\twords"))
        assertEquals("a".repeat(64), DefineTextActivity.oneWord("a".repeat(64)))
        val send = Intent(app, DefineTextActivity::class.java)
            .setAction(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "airport")
        val scenario = ActivityScenario.launchActivityForResult<DefineTextActivity>(send)
        idle()
        assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        assertTrue(lookups.isEmpty())
    }
}
