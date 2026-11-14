/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.phrasebook

import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.content.phrasebook.Phrase
import com.manjul.aikeyboard.core.content.phrasebook.PhraseCategory
import com.manjul.aikeyboard.core.datastore.FeatureSettings
import com.manjul.aikeyboard.core.datastore.LanguagePair
import com.manjul.aikeyboard.fork.testing.ComposeHostActivityRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PhrasebookViewModelTest {
    @get:Rule(order = 0)
    val host = ComposeHostActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val categories = listOf(PhraseCategory("favourites", 0, virtual = true), PhraseCategory("greetings", 10))
    private val calls = mutableListOf<Pair<String, String>>()
    private val ready = MutableStateFlow(true)

    private fun phrase(target: String) = Phrase(
        ref = "c:1", text = "Hello, nice to meet you.", translation = "[$target] Hello", machine = true,
        favourite = false, own = false,
    )

    private fun TestScope.vm(
        saved: SavedStateHandle = SavedStateHandle(),
        settings: FeatureSettings = FeatureSettings(),
    ) =
        PhrasebookViewModel(
            saved = saved,
            categories = { categories },
            phrases = { category, target ->
                calls += category to target
                flowOf(listOf(phrase(target)))
            },
            packReady = { _: String -> ready as Flow<Boolean> },
            settings = settings,
            scope = backgroundScope,
        )

    @Test
    fun theTargetIsTheTranslatorTargetElseTheFirstMvpLanguageThatIsNotEnglish() = runTestUnconfined {
        val first = vm()
        assertEquals("es", first.state.value.target)
        assertEquals(categories, first.state.value.categories)
        val pair = FeatureSettings(translatePair = LanguagePair(source = "en", target = "ur"))
        assertEquals("ur", vm(settings = pair).state.value.target)
        val english = FeatureSettings(translatePair = LanguagePair(source = "fr", target = "en"))
        assertEquals("es", vm(settings = english).state.value.target)
    }

    @Test
    fun aTargetChangeTranslatesAgain() = runTestUnconfined {
        val saved = SavedStateHandle()
        val vm = vm(saved)
        vm.openCategory("greetings")
        runCurrent()
        assertEquals(listOf("greetings" to "es"), calls)
        assertEquals("[es] Hello", vm.state.value.phrases.single().translation)

        vm.setTarget("fr")
        runCurrent()
        assertEquals("greetings" to "fr", calls.last())
        assertEquals("[fr] Hello", vm.state.value.phrases.single().translation)

        // The chosen target and category survive a restore.
        val restored = vm(saved)
        runCurrent()
        assertEquals("fr", restored.state.value.target)
        assertEquals("greetings", restored.state.value.category)
    }

    @Test
    fun aPackChangeTranslatesAgainAndTheMissingPackIsShown() = runTestUnconfined {
        ready.value = false
        val vm = vm()
        vm.openCategory("greetings")
        runCurrent()
        assertTrue(vm.state.value.packMissing)
        ready.value = true
        runCurrent()
        assertFalse(vm.state.value.packMissing)
        assertEquals(2, calls.size)
    }

    @Test
    fun sendViaKeyboardSharesTheShownTextAsPlainText() = runTestUnconfined {
        val vm = vm()
        val translated = phrase("es")
        val chooser = vm.shareIntent(translated)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = assertNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertEquals("[es] Hello", send.getStringExtra(Intent.EXTRA_TEXT))

        @Suppress("DEPRECATION")
        val english = vm.shareIntent(translated.copy(translation = null))
            .getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertEquals("Hello, nice to meet you.", english?.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("Hello, nice to meet you.", PhrasebookViewModel.shownText(translated.copy(translation = null)))
    }

    @Test
    fun theUiStateKeepsPhraseTextOutOfToString() = runTestUnconfined {
        val vm = vm()
        vm.openCategory("greetings")
        runCurrent()
        assertFalse(vm.state.value.toString().contains("Hello"))
    }

    @Test
    fun aRowShowsMachineTranslatedAndTheMissingPackOffersADownload() {
        var downloads = 0
        compose.setContent {
            ProvideLocalizedResources(LocalContext.current, appName = R.string.app_name) {
                PhraseListContent(
                    state = PhrasebookUiState(
                        target = "es",
                        category = "greetings",
                        phrases = listOf(
                            phrase("es"),
                            phrase("es").copy(ref = "c:2", text = "Bye.", translation = null, machine = false),
                        ),
                        packMissing = true,
                    ),
                    onSpeak = {},
                    onFavourite = {},
                    onShare = {},
                    onCopy = {},
                    onEdit = {},
                    onDelete = {},
                    onDownload = { downloads++ },
                )
            }
        }
        compose.onNodeWithText("[es] Hello").assertExists()
        compose.onNodeWithText("Machine translated").assertExists()
        compose.onNodeWithText("Bye.").assertExists()
        compose.onNodeWithText("Download language").performClick()
        assertEquals(1, downloads)
    }

    private fun runTestUnconfined(block: suspend TestScope.() -> Unit) =
        runTest(UnconfinedTestDispatcher()) { block() }
}
