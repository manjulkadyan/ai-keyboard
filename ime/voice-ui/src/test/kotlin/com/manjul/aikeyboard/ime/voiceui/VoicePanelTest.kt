/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.voiceui

import androidx.compose.foundation.layout.Row
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
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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
}
