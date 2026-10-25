/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translator

import android.content.ClipboardManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.translate.FailKind
import com.manjul.aikeyboard.core.translate.TranslateResult
import com.manjul.aikeyboard.core.translate.bar.DebouncePolicy
import com.manjul.aikeyboard.core.tts.TtsAvailability
import com.manjul.aikeyboard.fork.testing.ComposeHostActivityRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TranslatorScreenTest {
    @get:Rule(order = 0)
    val host = ComposeHostActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After
    fun tearDown() = scope.cancel()

    private fun show(results: MutableList<TranslateResult> = mutableListOf()): TranslatorRig {
        val rig = TranslatorRig(scope, results = results, debounce = DebouncePolicy(0))
        compose.setContent {
            ProvideLocalizedResources(LocalContext.current, appName = R.string.app_name) {
                val state by rig.vm.state.collectAsState()
                TranslatorContent(state = state, vm = rig.vm, onMic = {})
            }
        }
        return rig
    }

    private fun waitForText(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun counterShowsLengthAndLimit() {
        val rig = show()
        compose.runOnIdle { rig.vm.onInput("hello world!") }
        compose.onNodeWithText("12 / 5000").assertIsDisplayed()
    }

    @Test
    fun swapButtonSwapsLabels() {
        show()
        compose.onNodeWithContentDescription("Translate from English").assertIsDisplayed()
        compose.onNodeWithContentDescription("Swap languages").performClick()
        compose.onNodeWithContentDescription("Translate from Spanish").assertIsDisplayed()
        compose.onNodeWithContentDescription("Translate to English").assertIsDisplayed()
    }

    @Test
    fun copyPutsTranslationOnClipboard() {
        val rig = show()
        compose.runOnIdle { rig.vm.onInput("hello") }
        waitForText("<hello>")
        compose.onNodeWithContentDescription("Copy translation").performClick()
        val cm = RuntimeEnvironment.getApplication().getSystemService(ClipboardManager::class.java)
        assertEquals("<hello>", cm.primaryClip?.getItemAt(0)?.text?.toString())
    }

    @Test
    fun speakIsDisabledWithInstallVoiceWhenVoiceIsMissing() {
        val rig = show()
        rig.tts.availability = TtsAvailability.MISSING_DATA
        compose.runOnIdle { rig.vm.onInput("hello") }
        waitForText("Install voice")
        compose.onNodeWithContentDescription("Read aloud").assertIsNotEnabled()
        compose.onNodeWithText("Install voice").assertIsDisplayed()
    }

    @Test
    fun errorsRenderFriendlyCopy() {
        val rig = show(results = mutableListOf(TranslateResult.Failed(FailKind.TIMEOUT)))
        compose.runOnIdle { rig.vm.onInput("hello") }
        waitForText("Translation took too long. Try again")
        compose.onNodeWithText("Retry").assertIsDisplayed()
    }
}
