/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.smartbar

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import com.manjul.aikeyboard.fork.testing.ComposeHostActivityRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ChipSemanticsTest {
    @get:Rule(order = 0)
    val host = ComposeHostActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    @Test
    fun chipHasNameButtonRoleAndClickAction() {
        var clicks = 0
        compose.setContent { Box(Modifier.chipSemantics("Translate", enabled = true) { clicks++ }) }
        val node = compose.onNodeWithContentDescription("Translate")
        node.assertContentDescriptionEquals("Translate")
        node.assertHasClickAction()
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        node.performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, clicks)
    }

    @Test
    fun disabledChipIsNotEnabled() {
        compose.setContent { Box(Modifier.chipSemantics("Translate", enabled = false) {}) }
        compose.onNodeWithContentDescription("Translate").assertIsNotEnabled()
    }
}
