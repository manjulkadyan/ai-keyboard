/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.paywall

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.billing.Plan
import com.manjul.aikeyboard.fork.testing.ComposeHostActivityRule
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PaywallScreenTest {
    @get:Rule(order = 0)
    val host = ComposeHostActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val res = RuntimeEnvironment.getApplication().resources
    private var closes = 0
    private var buys = 0

    private fun show(ui: PaywallUi) = compose.setContent {
        ProvideLocalizedResources(LocalContext.current, appName = R.string.app_name) {
            PaywallScreen(
                ui = ui,
                actions = PaywallActions(
                    onClose = { closes++ },
                    onSelect = {},
                    onPurchase = { buys++ },
                    onRestore = {},
                    onRetry = {},
                    onTerms = {},
                    onPrivacy = {},
                ),
            )
        }
    }

    @Test
    fun theCloseButtonIsDisplayedOnTheFirstCompositionWhileLoading() {
        show(PaywallUi.loading(PaywallTrigger.ONBOARDING))
        compose.onNodeWithTag(PAYWALL_CLOSE_TAG).assertIsDisplayed()
        compose.onNodeWithTag(PAYWALL_CTA_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(PAYWALL_CLOSE_TAG).performClick()
        assertEquals(1, closes)
    }

    @Test
    fun pricesTrialTermsAndLinksAreShown() {
        val c = PaywallViewModelTest.CATALOG
        show(
            PaywallUi.loading(PaywallTrigger.AI_QUOTA).copy(
                annual = c.annual, monthly = c.monthly, selected = Plan.ANNUAL, savingsPercent = 44,
                trialTerms = "7 days free", ctaEnabled = true, loading = false,
            ),
        )
        compose.onNodeWithText(res.getString(R.string.paywall__save_percent, 44)).assertExists()
        compose.onNodeWithText("7 days free").assertExists()
        compose.onNodeWithText(res.getString(R.string.paywall__restore)).assertExists()
        compose.onNodeWithText(res.getString(R.string.paywall__terms)).assertExists()
        compose.onNodeWithText(res.getString(R.string.paywall__privacy)).assertExists()
        compose.onNodeWithTag(PAYWALL_CTA_TAG).assertIsEnabled().performClick()
        assertEquals(1, buys)
    }

    @Test
    fun aMissingCatalogShowsTheError() {
        show(PaywallUi.loading(PaywallTrigger.AI_QUOTA).copy(loading = false, pricesUnavailable = true))
        compose.onNodeWithText(res.getString(R.string.paywall__prices_unavailable)).assertExists()
        compose.onNodeWithTag(PAYWALL_CTA_TAG).assertIsNotEnabled()
    }

    @Test
    fun activatingIsShown() {
        show(PaywallUi.loading(PaywallTrigger.AI_QUOTA).copy(loading = false, activating = true))
        compose.onNodeWithText(res.getString(R.string.paywall__activating)).assertExists()
    }
}
