/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.database.TranslationHistoryEntity
import com.manjul.aikeyboard.core.database.TranslationHistoryRepository
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
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class HistoryScreenTest {
    @get:Rule(order = 0)
    val host = ComposeHostActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val dao = FakeHistoryDao()
    private var confirm by mutableStateOf(false)

    @After
    fun tearDown() = scope.cancel()

    private fun row(id: Long, source: String, target: String, favorite: Boolean = false) = TranslationHistoryEntity(
        id = id,
        sourceText = source,
        targetText = target,
        sourceLang = "en",
        targetLang = "es",
        engine = "mlkit",
        origin = "text",
        isFavorite = favorite,
        createdAt = id,
    )

    private fun show() {
        dao.rows.value = listOf(row(1, "cat", "gato"), row(2, "dog", "perro", favorite = true), row(3, "bird", "pájaro"))
        val vm = HistoryViewModel(TranslationHistoryRepository(dao), scope)
        compose.setContent {
            ProvideLocalizedResources(LocalContext.current, appName = R.string.app_name) {
                HistoryContent(vm, confirmClear = confirm, onConfirmClear = { confirm = it })
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("cat").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun shown(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun searchFilters() {
        show()
        compose.onNode(hasSetTextAction()).performTextInput("perr")
        compose.waitUntil(5_000) { !shown("cat") }
        compose.onNodeWithText("dog").assertIsDisplayed()
        assertTrue(!shown("bird"))
    }

    @Test
    fun favouriteToggles() {
        show()
        // Rows are newest first: bird (3), dog (2, starred), cat (1).
        compose.onAllNodesWithContentDescription("Add to favourites")[0].performClick()
        compose.waitUntil(5_000) { dao.rows.value.first { it.id == 3L }.isFavorite }
        compose.onAllNodesWithContentDescription("Remove from favourites")[0].performClick()
        compose.waitUntil(5_000) { dao.rows.value.count { it.isFavorite } == 1 }
    }

    @Test
    fun swipeDeletes() {
        show()
        compose.onNodeWithText("cat").performTouchInput { swipeRight() }
        compose.waitUntil(5_000) { dao.rows.value.none { it.id == 1L } }
        assertEquals(2, dao.rows.value.size)
    }

    @Test
    fun clearAllAsksAndKeepsFavouritesWhenChosen() {
        show()
        confirm = true
        compose.onNodeWithText("Clear translation history?").assertIsDisplayed()
        assertEquals(3, dao.rows.value.size)
        // "Keep favourites" is checked by default.
        compose.onNodeWithText("Clear").performClick()
        compose.waitUntil(5_000) { dao.rows.value.size == 1 }
        assertEquals("dog", dao.rows.value.single().sourceText)
    }

    @Test
    fun clearAllWithoutKeepingFavouritesDeletesEverything() {
        show()
        confirm = true
        compose.onNodeWithText("Keep favourites").performClick()
        compose.onNodeWithText("Clear").performClick()
        compose.waitUntil(5_000) { dao.rows.value.isEmpty() }
    }
}
