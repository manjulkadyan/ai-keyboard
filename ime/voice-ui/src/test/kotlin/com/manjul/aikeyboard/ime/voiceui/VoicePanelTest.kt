/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.voiceui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
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

    private fun showInSmartbar(
        state: VoiceUiState,
        onAllowMic: () -> Unit = {},
        onResume: () -> Unit = {},
        width: Dp = 360.dp,
        fontScale: Float = 1f,
    ) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            // A phone with the smartbar height (FlorisImeSizing.smartbarHeight, 40dp).
            Row(Modifier.width(width).height(40.dp)) {
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

    // Device check 4.6: at font scale 1.3 two lines no longer fit 40dp, and the chip was ellipsized on a Pixel 9.
    private fun assertPermissionChipFits(width: Dp, fontScale: Float) {
        val label = RuntimeEnvironment.getApplication().getString(R.string.voice__allow_mic)
        showInSmartbar(VoiceUiState.NeedsPermission, width = width, fontScale = fontScale)
        assertFalse(isCutOff(label), "cut off at $width, font scale $fontScale")
    }

    @Test fun thePermissionChipFitsAtFontScale130OnA360dpPhone() = assertPermissionChipFits(360.dp, 1.3f)

    @Test fun thePermissionChipFitsAtFontScale130OnA411dpPhone() = assertPermissionChipFits(411.dp, 1.3f)

    @Test fun thePermissionChipFitsAtFontScale200OnA360dpPhone() = assertPermissionChipFits(360.dp, 2f)

    @Test fun thePermissionChipFitsAtFontScale200OnA411dpPhone() = assertPermissionChipFits(411.dp, 2f)
}
