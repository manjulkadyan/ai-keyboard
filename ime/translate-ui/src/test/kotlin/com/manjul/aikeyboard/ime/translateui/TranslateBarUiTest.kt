/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.translateui

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import com.manjul.aikeyboard.core.datastore.BarMode
import com.manjul.aikeyboard.core.translate.FailKind
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.translate.LangPair
import com.manjul.aikeyboard.core.translate.bar.BarStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
// Native graphics: the legacy mode does not lay out text, so alignment cannot be measured.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TranslateBarUiTest {
    @get:Rule val compose = createComposeRule()

    private val app = RuntimeEnvironment.getApplication()
    private fun str(@StringRes id: Int, vararg args: Any): String = app.getString(id, *args)

    private val enUr = LangPair(Lang("en"), Lang("ur"))

    private fun open(
        input: String = "hello",
        preview: String? = "ہیلو",
        status: BarStatus = BarStatus.Ok,
        undoAvailable: Boolean = false,
        inFlight: Boolean = false,
    ) = BarUiState.Open(input, preview, enUr, BarMode.PREVIEW, status, undoAvailable, inFlight)

    private val calls = mutableListOf<String>()

    private fun show(state: BarUiState.Open) = compose.setContent {
        TranslateBar(
            state = state,
            onSwap = { calls += "swap" },
            onInsert = { calls += "insert" },
            onUndo = { calls += "undo" },
            onClose = { calls += "close" },
            onDownload = { calls += "download" },
            onOpenOfflineLanguages = { calls += "offline" },
            onRetry = { calls += "retry" },
        )
    }

    // Status chips

    @Test fun needsDownloadOffersTheDownloadWithSystemLanguageNamesAndSize() {
        show(open(status = BarStatus.NeedsDownload(enUr, bytes = 30_600_000)))
        val label = str(R.string.translate_bar__status_download, "English", "Urdu", 31)
        assertEquals("Download English↔Urdu (about 31 MB)", label)
        compose.onNodeWithText(label).assertIsDisplayed().performClick()
        assertEquals(listOf("download"), calls)
    }

    @Test fun offlineOpensTheOfflineLanguagesScreen() {
        show(open(status = BarStatus.Offline))
        compose.onNodeWithText(str(R.string.translate_bar__status_offline)).assertIsDisplayed().performClick()
        assertEquals(listOf("offline"), calls)
    }

    @Test fun anOfflineFailureAlsoOpensTheOfflineLanguagesScreen() {
        show(open(status = BarStatus.Error(FailKind.OFFLINE)))
        compose.onNodeWithText(str(R.string.translate_bar__status_offline)).performClick()
        assertEquals(listOf("offline"), calls)
    }

    @Test fun aTransientFailureOffersRetry() {
        // setContent runs once per test, so each kind is shown by changing the state.
        var status by mutableStateOf<BarStatus>(BarStatus.Error(FailKind.TIMEOUT))
        compose.setContent {
            BarStatusChip(status, { calls += "download" }, { calls += "offline" }, { calls += "retry" })
        }
        val kinds = listOf(FailKind.TIMEOUT, FailKind.QUOTA, FailKind.ENGINE)
        for (kind in kinds) {
            status = BarStatus.Error(kind)
            compose.onNodeWithText(str(R.string.translate_bar__status_retry)).assertIsDisplayed().performClick()
        }
        assertEquals(kinds.map { "retry" }, calls)
    }

    @Test fun cloudOnlyProIsInformational() {
        show(open(status = BarStatus.CloudOnlyPro))
        compose.onNodeWithText(str(R.string.translate_bar__status_cloud_pro)).assertIsDisplayed()
            .assertHasNoClickAction()
    }

    @Test fun anUnsupportedPairIsInformational() {
        show(open(status = BarStatus.Error(FailKind.UNSUPPORTED)))
        compose.onNodeWithText(str(R.string.translate_bar__status_unsupported)).assertIsDisplayed()
            .assertHasNoClickAction()
    }

    @Test fun noStatusChipWhenIdleOrOk() {
        show(open(status = BarStatus.Ok))
        listOf(
            R.string.translate_bar__status_offline,
            R.string.translate_bar__status_retry,
            R.string.translate_bar__status_cloud_pro,
            R.string.translate_bar__status_unsupported,
        ).forEach { compose.onNodeWithText(str(it)).assertDoesNotExist() }
    }

    // Undo chip

    @Test fun undoChipRunsTheCallbackAndHidesAfterFiveSeconds() {
        compose.mainClock.autoAdvance = false
        show(open(undoAvailable = true))
        val undo = str(R.string.translate_bar__undo_cd)
        compose.mainClock.advanceTimeBy(UNDO_WINDOW_MS - 100)
        compose.onNodeWithContentDescription(undo).assertIsDisplayed().performClick()
        assertEquals(listOf("undo"), calls)
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithContentDescription(undo).assertDoesNotExist()
    }

    @Test fun undoChipRestartsItsWindowOnANewInsert() {
        compose.mainClock.autoAdvance = false
        var state by mutableStateOf(open(undoAvailable = true).copy(undoId = 1))
        compose.setContent {
            TranslateBar(state, onSwap = {}, onInsert = {}, onUndo = {}, onClose = {}, onDownload = {},
                onOpenOfflineLanguages = {}, onRetry = {})
        }
        val undo = str(R.string.translate_bar__undo_cd)
        compose.mainClock.advanceTimeBy(UNDO_WINDOW_MS - 1_000)
        // A second insert within the window: undoAvailable stays true, the id changes.
        state = state.copy(undoId = 2)
        // Lets the new state reach the composition before the clock moves on.
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(UNDO_WINDOW_MS - 1_000)
        compose.onNodeWithContentDescription(undo).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1_100)
        compose.onNodeWithContentDescription(undo).assertDoesNotExist()
    }

    @Test fun noUndoChipWithoutAnUndo() {
        show(open(undoAvailable = false))
        compose.onNodeWithContentDescription(str(R.string.translate_bar__undo_cd)).assertDoesNotExist()
    }

    // Buffer line

    @Test fun rtlBufferRendersRightAligned() {
        show(open(input = "سلام دنیا"))
        val layout = bufferLayout()
        assertEquals(ResolvedTextDirection.Rtl, layout.getParagraphDirection(0))
        assertEquals(layout.size.width.toFloat(), layout.getLineRight(0), absoluteTolerance = 1f)
        assertTrue(layout.getLineLeft(0) > layout.size.width / 2f, "RTL text starts at the right edge")
    }

    @Test fun ltrBufferRendersLeftAligned() {
        show(open(input = "hello"))
        val layout = bufferLayout()
        assertEquals(ResolvedTextDirection.Ltr, layout.getParagraphDirection(0))
        assertEquals(0f, layout.getLineLeft(0), absoluteTolerance = 1f)
    }

    @Test fun emptyBufferShowsTheHint() {
        show(open(input = "", preview = null))
        compose.onNodeWithText(str(R.string.translate_bar__buffer_hint)).assertIsDisplayed()
    }

    private fun bufferLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(BUFFER_TAG).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult]
            .action!!(results)
        return results.single()
    }

    // Pair chip, preview, buttons

    @Test fun pairChipShowsSystemLanguageNamesAndSwaps() {
        show(open())
        compose.onNodeWithText("English", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Urdu", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription(str(R.string.translate_bar__pair_cd, "English", "Urdu"))
            .assertIsDisplayed()
        compose.onNodeWithContentDescription(str(R.string.translate_bar__swap_cd, "English", "Urdu")).performClick()
        assertEquals(listOf("swap"), calls)
    }

    @Test fun previewIsAPoliteLiveRegionAndInFlightShowsTheIndicator() {
        show(open(preview = "ہیلو", inFlight = true))
        compose.onNodeWithTag(PREVIEW_TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf(str(R.string.translate_bar__preview_announcement, "ہیلو")),
                ),
            )
        compose.onNodeWithContentDescription(str(R.string.translate_bar__translating_cd)).assertIsDisplayed()
    }

    @Test fun noIndicatorWhenNothingIsInFlight() {
        show(open(inFlight = false))
        compose.onNodeWithContentDescription(str(R.string.translate_bar__translating_cd)).assertDoesNotExist()
    }

    @Test fun insertAndCloseRunTheirCallbacks() {
        show(open())
        compose.onNodeWithContentDescription(str(R.string.translate_bar__insert_cd)).assertIsEnabled().performClick()
        compose.onNodeWithContentDescription(str(R.string.translate_bar__close_cd)).performClick()
        assertEquals(listOf("insert", "close"), calls)
    }

    @Test fun insertIsDisabledWithAnEmptyBuffer() {
        show(open(input = "", preview = null))
        compose.onNodeWithContentDescription(str(R.string.translate_bar__insert_cd)).assertIsNotEnabled()
    }

    @Test fun insertIsEnabledBeforeThePreviewArrives() {
        // Enter or ✓ before the translation commits it when it arrives.
        show(open(preview = null))
        compose.onNodeWithContentDescription(str(R.string.translate_bar__insert_cd)).assertIsEnabled().performClick()
        assertEquals(listOf("insert"), calls)
    }

    @Test fun aPendingInsertShowsProgressOnTheCheckButton() {
        show(open(preview = null, inFlight = true).copy(commitPending = true))
        compose.onNodeWithContentDescription(str(R.string.translate_bar__insert_pending_cd))
            .assertIsDisplayed()
            .assertIsNotEnabled()
        compose.onNodeWithContentDescription(str(R.string.translate_bar__insert_cd)).assertDoesNotExist()
        // One indicator, on ✓: the preview line's in-flight indicator steps aside.
        compose.onNodeWithContentDescription(str(R.string.translate_bar__translating_cd)).assertDoesNotExist()
    }
}
