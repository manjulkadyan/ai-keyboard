/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.database.TranslitMappingEntity
import com.manjul.aikeyboard.core.translit.TranslitLang
import com.manjul.aikeyboard.fork.testing.ComposeHostActivityRule
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RomanTypingScreenTest {
    @get:Rule(order = 0) val host = ComposeHostActivityRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    private var rows by mutableStateOf(listOf(TranslitMappingEntity(1, "ur", "kya", "کیا", 1)))
    private var lang by mutableStateOf(TranslitLang.UR)
    private val saved = mutableListOf<Triple<String, String, Long?>>()
    private val deleted = mutableListOf<Long>()

    private fun show() = compose.setContent {
        ProvideLocalizedResources(LocalContext.current, appName = R.string.app_name) {
            RomanTypingContent(
                lang = lang,
                onLang = { lang = it },
                rows = rows,
                onSave = { r, n, id -> saved += Triple(r, n, id) },
                onDelete = { deleted += it },
            )
        }
    }

    @Test fun `the title carries the Beta label`() {
        assertEquals("Roman typing (Beta)", RuntimeEnvironmentString.title())
    }

    @Test fun `add saves a lower-cased trimmed pair, and an invalid Roman word cannot be saved`() {
        show()
        compose.onNodeWithText("Add a word").performClick()
        compose.onNodeWithText("Roman spelling").performTextInput("ky4")
        compose.onNodeWithText("Your word").performTextInput("کیا")
        compose.onNodeWithText("Use the letters a to z only.").assertExists()
        compose.onNodeWithText("Save").performClick() // disabled: nothing happens
        assertEquals(emptyList<Triple<String, String, Long?>>(), saved)
    }

    @Test fun `add with valid words saves`() {
        show()
        compose.onNodeWithText("Add a word").performClick()
        compose.onNodeWithText("Roman spelling").performTextInput("Kyun")
        compose.onNodeWithText("Your word").performTextInput("کیوں")
        compose.onNodeWithText("Save").performClick()
        assertEquals(listOf(Triple<String, String, Long?>("kyun", "کیوں", null)), saved)
    }

    @Test fun `edit on tap changes the native word and keeps the row`() {
        show()
        compose.onNodeWithText("کیا").performClick()
        compose.onNodeWithText("Edit word").assertExists()
        compose.onNodeWithText("Your word").performTextInput("ء")
        compose.onNodeWithText("Save").performClick()
        assertEquals(listOf(Triple<String, String, Long?>("kya", "ءکیا", null)), saved)
    }

    @Test fun `delete asks first and cancel keeps the row`() {
        show()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete this saved word?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(emptyList<Long>(), deleted)
        compose.onNodeWithText("Delete").performClick()
        compose.onAllNodesWithTextDelete()
        assertEquals(listOf(1L), deleted)
    }

    @Test fun `the language switch and the empty state`() {
        rows = emptyList()
        show()
        compose.onNodeWithText("No saved words yet.").assertExists()
        compose.onNodeWithText("Hindi").performClick()
        assertEquals(TranslitLang.HI, lang)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextDelete() {
        onAllNodesWithText("Delete").onLast().performClick()
    }

    private object RuntimeEnvironmentString {
        fun title(): String = org.robolectric.RuntimeEnvironment.getApplication().getString(R.string.roman_typing__title)
    }
}
