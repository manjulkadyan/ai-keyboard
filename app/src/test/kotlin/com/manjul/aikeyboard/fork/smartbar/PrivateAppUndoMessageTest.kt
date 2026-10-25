/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.smartbar

import android.app.Application
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.manjul.aikeyboard.fork.testing.ComposeHostActivityRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PrivateAppUndoMessageTest {
    @get:Rule(order = 0)
    val host = ComposeHostActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    @Test
    fun `shows the message as a polite live region and Undo has a 48 dp target`() {
        var undos = 0
        compose.setContent { PrivateAppUndoMessage(onUndo = { undos++ }) }

        compose.onNodeWithTag(PRIVATE_APP_UNDO_MESSAGE_TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithText("App made private").assertIsDisplayed()
        compose.onNodeWithText("Undo")
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
            .performClick()

        assertEquals(1, undos)
    }
}
