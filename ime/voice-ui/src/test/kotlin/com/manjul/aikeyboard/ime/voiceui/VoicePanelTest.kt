/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.voiceui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
// Native graphics: the legacy mode does not measure text, so line breaks and ellipsis are meaningless.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VoicePanelTest {
    @get:Rule val compose = createComposeRule()

    private val chip = RuntimeEnvironment.getApplication().getString(R.string.voice__download_whisper)

    private fun show(state: VoiceUiState, onDownloadWhisperModel: (() -> Unit)?) = compose.setContent {
        Row {
            VoicePanel(
                state = state,
                onStop = {},
                onDismiss = {},
                onAllowMic = {},
                onResume = {},
                onDownloadSystemModel = null,
                onDownloadWhisperModel = onDownloadWhisperModel,
            )
        }
    }

    @Test fun theWhisperDownloadOfferIsAChipThatRunsTheCallback() {
        var taps = 0
        show(VoiceUiState.Message(MessageKind.NO_SERVICE, offerWhisperDownload = true)) { taps++ }
        compose.onNodeWithText(chip).assertIsDisplayed().performClick()
        assertEquals(1, taps)
    }

    @Test fun noChipWithoutTheOffer() {
        show(VoiceUiState.Message(MessageKind.NO_SERVICE, offerWhisperDownload = false)) {}
        compose.onNodeWithText(chip).assertDoesNotExist()
    }

    @Test fun noChipWithoutAWayToDownload() {
        show(VoiceUiState.Message(MessageKind.NO_SERVICE, offerWhisperDownload = true), onDownloadWhisperModel = null)
        compose.onNodeWithText(chip).assertDoesNotExist()
    }

    /** Whether the laid-out [text] lost its end to an ellipsis or to a line that does not fit the height. */
    private fun isCutOff(text: String): Boolean {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
        val layout = results.single()
        return layout.isLineEllipsized(layout.lineCount - 1) || layout.didOverflowHeight
    }

    private fun showInSmartbar(state: VoiceUiState, onAllowMic: () -> Unit = {}, onResume: () -> Unit = {}) =
        compose.setContent {
            // A ~360dp phone with the smartbar height (FlorisImeSizing.smartbarHeight, 40dp).
            Row(Modifier.width(360.dp).height(40.dp)) {
                VoicePanel(
                    state = state,
                    onStop = {},
                    onDismiss = {},
                    onAllowMic = onAllowMic,
                    onResume = onResume,
                    onDownloadSystemModel = null,
                    onDownloadWhisperModel = null,
                )
            }
        }

    @Test fun thePermissionChipIsNotCutOffOnAPhone() {
        val label = RuntimeEnvironment.getApplication().getString(R.string.voice__allow_mic)
        var taps = 0
        showInSmartbar(VoiceUiState.NeedsPermission, onAllowMic = { taps++ })
        assertFalse(isCutOff(label))
        compose.onNodeWithText(label).performClick()
        assertEquals(1, taps)
    }

    @Test fun theResumeChipIsNotCutOffOnAPhone() {
        val label = RuntimeEnvironment.getApplication().getString(R.string.voice__tap_to_resume)
        var taps = 0
        showInSmartbar(VoiceUiState.TapToResume, onResume = { taps++ })
        assertFalse(isCutOff(label))
        compose.onNodeWithText(label).performClick()
        assertEquals(1, taps)
    }
}
