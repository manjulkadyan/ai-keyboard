/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.dictionary

import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.app.FlorisAppActivity
import com.manjul.aikeyboard.core.content.dictionary.Definition
import com.manjul.aikeyboard.core.content.dictionary.LookupResult
import com.manjul.aikeyboard.fork.routes.AppIntent
import com.manjul.aikeyboard.fork.routes.AppIntents
import com.manjul.aikeyboard.fork.routes.RouteRegistry
import com.manjul.aikeyboard.fork.testing.ComposeHostActivityRule
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DefinitionCardTest {
    @get:Rule(order = 0)
    val host = ComposeHostActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val app = RuntimeEnvironment.getApplication()
    private val found = LookupResult(
        definitions = listOf(Definition("book", "noun", "Pages bound together.", null, emptyList())),
        offline = true,
        attribution = null,
    )

    @After
    fun tearDown() = DefinitionCards.dismiss()

    private fun render(ui: DefinitionCardUi, onMore: () -> Unit = {}, onRemove: () -> Unit = {}) {
        compose.setContent {
            ProvideLocalizedResources(LocalContext.current, appName = R.string.app_name) {
                DefinitionCardContent(ui = ui, onMore = onMore, onRemove = onRemove, onClose = {})
            }
        }
    }

    @Test
    fun aLearnedWordOffersRemoveSuggestion() {
        var removed = 0
        render(DefinitionCardUi("book", learned = true, result = found), onRemove = { removed++ })
        compose.onNodeWithText("Pages bound together.").assertExists()
        compose.onNodeWithText("Remove suggestion").performClick()
        assertEquals(1, removed)
    }

    @Test
    fun aDictionaryWordHasNoRemoveSuggestion() {
        render(DefinitionCardUi("book", learned = false, result = found))
        compose.onNodeWithText("More").assertExists()
        compose.onNodeWithText("Remove suggestion").assertDoesNotExist()
    }

    @Test
    fun noPackAsksForTheDownloadInTheApp() {
        val missing = LookupResult(emptyList(), offline = true, attribution = null, packMissing = true)
        render(DefinitionCardUi("book", learned = false, result = missing))
        compose.onNodeWithText("Download the dictionary in the app").assertExists()
    }

    @Test
    fun moreBuildsTheDictionaryIntentWithTheWord() {
        val intent = DefinitionCards.moreIntent(app, "book")
        assertEquals(FlorisAppActivity::class.java.name, intent.component?.className)
        assertEquals(RouteRegistry.DICTIONARY, intent.getStringExtra(RouteRegistry.EXTRA_OPEN_SCREEN))
        assertEquals("book", intent.getStringExtra(RouteRegistry.EXTRA_DICTIONARY_WORD))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        // The route allow-list accepts it and hands the word on.
        val open = AppIntents.classify(intent) {} as AppIntent.OpenRoute
        assertEquals("book", open.dictionaryWord)
    }

    @Test
    fun removeRunsUpstreamOnlyForALearnedWord() {
        var removed = 0
        DefinitionCards.show("book", learned = false, lookup = { found }, remove = { removed++ > -1 })
        DefinitionCards.removeSuggestion()
        assertEquals(0, removed)
        DefinitionCards.show("book", learned = true, lookup = { found }, remove = { removed++ > -1 })
        DefinitionCards.removeSuggestion()
        assertEquals(1, removed)
        assertNull(DefinitionCards.state.value)
    }
}
