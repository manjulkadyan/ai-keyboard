/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.dictionary

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.content.dictionary.Attribution
import com.manjul.aikeyboard.core.content.dictionary.Definition
import com.manjul.aikeyboard.core.content.dictionary.LookupResult
import com.manjul.aikeyboard.fork.testing.ComposeHostActivityRule
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DictionaryViewModelTest {
    @get:Rule(order = 0)
    val host = ComposeHostActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val attribution = Attribution(
        source = "Wiktionary",
        attribution = "Wiktionary contributors",
        licenceName = "CC BY-SA 4.0",
        licenceUrl = "https://creativecommons.org/licenses/by-sa/4.0/",
    )
    private val found = LookupResult(
        definitions = listOf(Definition("run", "verb", "To move fast on foot.", "I run home.", listOf("sprint"))),
        offline = true,
        attribution = attribution,
    )
    private val calls = mutableListOf<Pair<String, Boolean>>()
    private val online = MutableStateFlow(false)

    private fun TestScope.vm(saved: SavedStateHandle = SavedStateHandle()) = DictionaryViewModel(
        saved = saved,
        lookup = { word, allowNetwork ->
            calls += word to allowNetwork
            found
        },
        connectivity = online,
        scope = backgroundScope,
    )

    @Test
    fun queryAndWordSurviveARestoreAndTheLookupRunsAgain() = runTestUnconfined {
        val saved = SavedStateHandle()
        val first = vm(saved)
        first.onQueryChange("run")
        first.submit()
        assertEquals(listOf("run" to false), calls)

        // A new view model over the same SavedStateHandle stands in for process death and restore.
        val restored = vm(saved)
        runCurrent()
        assertEquals("run", restored.state.value.query)
        assertEquals("run", restored.state.value.submitted)
        assertEquals(found, restored.state.value.result)
        assertEquals(2, calls.size)
    }

    @Test
    fun aConnectivityChangeRunsTheWordAgainAndKeepsTheQuery() = runTestUnconfined {
        val vm = vm()
        vm.onQueryChange("run")
        vm.submit()
        online.value = true
        runCurrent()
        assertEquals(listOf("run" to false, "run" to true), calls)
        assertEquals("run", vm.state.value.query)
        assertTrue(vm.state.value.online)

        online.value = false
        runCurrent()
        assertEquals("run", vm.state.value.query)
        assertEquals(found, vm.state.value.result)
        assertFalse(vm.state.value.loading)
        assertEquals("run" to false, calls.last())
    }

    @Test
    fun aBlankQueryLooksNothingUpAndTheQueryIsCapped() = runTestUnconfined {
        val vm = vm()
        vm.onQueryChange("   ")
        vm.submit()
        assertTrue(calls.isEmpty())
        vm.onQueryChange("a".repeat(100))
        assertEquals(64, vm.state.value.query.length)
    }

    @Test
    fun theEntryShowsTheSenseAndTheFooterShowsAttributionAndLicence() {
        var opened: String? = null
        compose.setContent {
            ProvideLocalizedResources(LocalContext.current, appName = R.string.app_name) {
                DictionaryContent(
                    state = DictionaryUiState(query = "run", submitted = "run", result = found),
                    onQueryChange = {},
                    onSubmit = {},
                    onSpeak = {},
                    onOpenLicence = { opened = it },
                )
            }
        }
        compose.onNodeWithText("verb").assertExists()
        compose.onNodeWithText("1. To move fast on foot.").assertExists()
        compose.onNodeWithText("Example: I run home.").assertExists()
        compose.onNodeWithText("Synonyms: sprint").assertExists()
        compose.onNodeWithText("Definitions from Wiktionary, by Wiktionary contributors").assertExists()
        compose.onNodeWithText("Licence: CC BY-SA 4.0").assertExists()
        compose.onNodeWithText("Licence: CC BY-SA 4.0").performClick()
        assertEquals(attribution.licenceUrl, opened)
    }

    private fun runTestUnconfined(block: suspend TestScope.() -> Unit) =
        runTest(UnconfinedTestDispatcher()) { block() }
}
