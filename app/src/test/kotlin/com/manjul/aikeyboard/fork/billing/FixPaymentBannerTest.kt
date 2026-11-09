/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.billing

import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.ai.kb.SubState
import com.manjul.aikeyboard.core.billing.ProUiState
import com.manjul.aikeyboard.fork.testing.ComposeHostActivityRule
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class FixPaymentBannerTest {
    @get:Rule(order = 0)
    val host = ComposeHostActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private var fixes = 0

    private fun show(state: SubState) = compose.setContent {
        ProvideLocalizedResources(LocalContext.current, appName = R.string.app_name) {
            FixPaymentBanner(ProUiState(isPro = state == SubState.GRACE, state, 0L, false), onFix = { fixes++ })
        }
    }

    @Test fun shownInGrace() {
        show(SubState.GRACE)
        compose.onNodeWithTag(FIX_PAYMENT_BANNER_TAG).assertExists()
    }

    @Test fun shownOnHold() {
        show(SubState.ON_HOLD)
        compose.onNodeWithTag(FIX_PAYMENT_BANNER_TAG).assertExists()
    }

    @Test fun hiddenForActive() = assertHidden(SubState.ACTIVE)

    @Test fun hiddenForPaused() = assertHidden(SubState.PAUSED)

    @Test fun hiddenForNone() = assertHidden(SubState.NONE)

    @Test fun hiddenForExpired() = assertHidden(SubState.EXPIRED)

    private fun assertHidden(state: SubState) {
        show(state)
        compose.onNodeWithTag(FIX_PAYMENT_BANNER_TAG).assertDoesNotExist()
    }

    @Test fun tapCallsFix() {
        show(SubState.ON_HOLD)
        compose.onNodeWithTag(FIX_PAYMENT_BANNER_TAG).performClick()
        compose.waitForIdle()
        assertEquals(1, fixes)
    }

    @Test fun manageSubscriptionLinkNamesTheProductAndPackage() {
        assertEquals(
            "https://play.google.com/store/account/subscriptions?sku=pro&package=com.example.kb",
            PlayLinks.manageSubscription("com.example.kb").toString(),
        )
    }

    @Test fun openManageSubscriptionStartsTheLink() {
        val app = RuntimeEnvironment.getApplication()
        PlayLinks.openManageSubscription(app)
        val started = assertNotNull(shadowOf(app).nextStartedActivity)
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(PlayLinks.manageSubscription(app.packageName), started.data)
    }
}
