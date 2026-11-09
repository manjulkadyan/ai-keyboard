/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.settings

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.ai.kb.SubState
import com.manjul.aikeyboard.core.billing.ProUiState
import com.manjul.aikeyboard.fork.billing.FIX_PAYMENT_BANNER_TAG
import com.manjul.aikeyboard.fork.testing.ComposeHostActivityRule
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SubscriptionSettingsTest {
    @get:Rule(order = 0)
    val host = ComposeHostActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    // EntitlementRepositoryTest covers the restore_purchases{found} event. This test only counts the calls.
    private var restores = 0
    private var manage = 0
    private var removeAds = 0

    private val free = ProUiState(isPro = false, state = SubState.NONE, expiresAtMs = 0L, activating = false)

    private fun show(state: ProUiState, found: Boolean) = show(state) { found }

    private fun show(state: ProUiState, restore: suspend () -> Boolean) = compose.setContent {
        ProvideLocalizedResources(LocalContext.current, appName = R.string.app_name) {
            SubscriptionSettingsContent(
                state = state,
                onManage = { manage++ },
                onRestore = { restores++; restore() },
                onRemoveAds = { removeAds++ },
            )
        }
    }

    @Test fun restoreWithAPurchaseShowsProRestored() {
        show(free, found = true)
        compose.onNodeWithText("Restore purchases").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Pro restored").assertExists()
        assertEquals(1, restores)
    }

    @Test fun restoreWithoutAPurchaseShowsNoPurchaseFound() {
        show(free, found = false)
        compose.onNodeWithText("Restore purchases").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("No purchase found").assertExists()
        assertEquals(1, restores)
    }

    @Test fun aFailedRestoreShowsNoPurchaseFoundAndCanBeRetried() {
        show(free) { throw IllegalStateException("billing unavailable") }
        compose.onNodeWithText("Restore purchases").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("No purchase found").assertExists()
        compose.onNodeWithText("Restore purchases").performClick()
        compose.waitForIdle()
        assertEquals(2, restores)
    }

    @Test fun aFreeUserCanRemoveAdsAndManageInPlay() {
        show(free, found = false)
        compose.onNodeWithText("Free").assertExists()
        compose.onNodeWithText("Remove ads").performScrollTo().performClick()
        compose.onNodeWithText("Manage in Google Play").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(1, removeAds)
        assertEquals(1, manage)
    }

    @Test fun aProUserInGraceSeesTheBannerAndNoRemoveAds() {
        val grace =
            ProUiState(isPro = true, state = SubState.GRACE, expiresAtMs = 1_800_000_000_000L, activating = false)
        show(grace, found = true)
        compose.onNodeWithTag(FIX_PAYMENT_BANNER_TAG).assertExists()
        compose.onNodeWithText("Remove ads").assertDoesNotExist()
        compose.onNodeWithText("Renews", substring = true).assertExists()
    }
}
