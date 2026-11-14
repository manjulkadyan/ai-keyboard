/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translit

import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.content.dictionary.LookupResult
import com.manjul.aikeyboard.core.translit.Source
import com.manjul.aikeyboard.core.translit.TranslitLang
import com.manjul.aikeyboard.fork.dictionary.DefinitionCardContent
import com.manjul.aikeyboard.fork.dictionary.DefinitionCardUi
import com.manjul.aikeyboard.fork.dictionary.DefinitionCards
import com.manjul.aikeyboard.fork.suggest.SuggestContext
import com.manjul.aikeyboard.fork.testing.ComposeHostActivityRule
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import kotlinx.coroutines.Dispatchers
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SaveMappingTest {
    @get:Rule(order = 0)
    val host = ComposeHostActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val context = SuggestContext()
    private var incognito = false
    private val provider = TranslitSuggestionProvider(
        { TranslitGraph({ error("no assets") }, Dispatchers.Unconfined) },
        context,
        { incognito },
    )

    @After
    fun tearDown() {
        DefinitionCards.dismiss()
        DefinitionCards.saveMappingHandler = null
    }

    private fun field(options: Int = 0) = context.update(
        EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; imeOptions = options },
        PolicyDecision.Unrestricted,
    )

    private fun candidate(latin: Boolean = false) =
        TranslitCandidate("کیا", 1.0, true, provider, "kya", TranslitLang.UR, Source.LEXICON, latin)

    private fun render(ui: DefinitionCardUi, onSave: () -> Unit = {}) {
        compose.setContent {
            ProvideLocalizedResources(LocalContext.current, appName = R.string.app_name) {
                DefinitionCardContent(ui = ui, onMore = {}, onRemove = {}, onClose = {}, onSaveMapping = onSave)
            }
        }
    }

    @Test fun `a native candidate gets the action`() {
        field()
        assertNotNull(DefinitionCards.mappingFor(candidate()))
        render(DefinitionCardUi("کیا", learned = false, mapping = candidate()))
        compose.onNodeWithText("Save mapping").assertExists()
    }

    @Test fun `incognito hides it`() {
        field()
        incognito = true
        assertNull(DefinitionCards.mappingFor(candidate()))
    }

    @Test fun `a no-learning field hides it`() {
        context.update(
            EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT },
            PolicyDecision.Unrestricted.copy(allowLearning = false),
        )
        assertNull(DefinitionCards.mappingFor(candidate()))
    }

    @Test fun `the Latin chip hides it`() {
        field()
        assertNull(DefinitionCards.mappingFor(candidate(latin = true)))
        render(DefinitionCardUi("kya", learned = false))
        compose.onNodeWithText("Save mapping").assertDoesNotExist()
    }

    @Test fun `a tap calls the save callback once`() {
        field()
        var saved = 0
        render(DefinitionCardUi("کیا", learned = false, mapping = candidate()), onSave = { saved++ })
        compose.onNodeWithText("Save mapping").performClick()
        assertEquals(1, saved)
    }

    @Test fun `the card saves through the handler once and then shows Mapping saved`() {
        field()
        val saved = mutableListOf<TranslitCandidate>()
        DefinitionCards.saveMappingHandler = { saved += it }
        val lookup = LookupResult(emptyList(), offline = true, attribution = null)
        DefinitionCards.show("کیا", false, lookup = { lookup }, remove = { false }, mapping = candidate())
        DefinitionCards.saveMapping()
        DefinitionCards.saveMapping()
        assertEquals(1, saved.size)
        assertEquals(true, DefinitionCards.state.value?.mappingSaved)
    }
}
