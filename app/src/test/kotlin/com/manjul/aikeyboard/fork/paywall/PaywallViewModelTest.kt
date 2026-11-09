/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.paywall

import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.ai.kb.SubState
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.billing.Plan
import com.manjul.aikeyboard.core.billing.ProCatalog
import com.manjul.aikeyboard.core.billing.ProOffer
import com.manjul.aikeyboard.core.billing.ProUiState
import com.manjul.aikeyboard.core.billing.RefreshReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private object FakeStrings : PaywallStrings {
    override fun trial(days: Int, yearlyPrice: String) = "$days days free, then $yearlyPrice/year"
    override fun yearly(price: String) = "$price/year"
    override fun monthly(price: String) = "$price/month"
}

class PaywallViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val events = mutableListOf<AnalyticsEvent>()
    private val refreshes = mutableListOf<RefreshReason>()
    private val pro = MutableStateFlow(ProUiState(isPro = false, SubState.NONE, 0L, activating = false))
    private var now = 1_000_000L

    @After
    fun tearDown() = scope.cancel()

    private fun vm(
        trigger: PaywallTrigger = PaywallTrigger.AI_QUOTA,
        restore: suspend () -> Boolean = { false },
        catalog: suspend () -> ProCatalog? = { CATALOG },
    ) = PaywallViewModel(
        trigger = trigger,
        catalog = catalog,
        refresh = { refreshes += it },
        restore = restore,
        pro = pro,
        strings = FakeStrings,
        analytics = { events += it },
        clock = { now },
        scope = scope,
    )

    private fun named(name: String) = events.filter { it.name == name }

    @Test
    fun annualIsSelectedByDefault() {
        val vm = vm().apply { open() }
        val ui = vm.ui.value
        assertEquals(Plan.ANNUAL, ui.selected)
        assertEquals(CATALOG.annual, ui.annual)
        assertEquals(CATALOG.monthly, ui.monthly)
        assertTrue(ui.ctaEnabled)
        assertFalse(ui.loading)
    }

    @Test
    fun savingsIs44ForTheOd02Prices() {
        assertEquals(44, vm().apply { open() }.ui.value.savingsPercent)
    }

    @Test
    fun trialTermsUseTheOfferPriceAndTrialDays() {
        val vm = vm().apply { open() }
        assertEquals("7 days free, then $19.99/year", vm.ui.value.trialTerms)
        vm.select(Plan.MONTHLY)
        assertEquals(Plan.MONTHLY, vm.ui.value.selected)
        assertEquals("$2.99/month", vm.ui.value.trialTerms)
    }

    @Test
    fun anAnnualPlanWithoutATrialShowsTheRenewalTerms() {
        val noTrial = CATALOG.copy(annual = CATALOG.annual.copy(trialDays = null))
        assertEquals("$19.99/year", vm(catalog = { noTrial }).apply { open() }.ui.value.trialTerms)
    }

    @Test
    fun aNullCatalogDisablesTheCtaAndShowsNoPrice() {
        var answer: ProCatalog? = null
        val vm = vm(catalog = { answer }).apply { open() }
        val ui = vm.ui.value
        assertFalse(ui.ctaEnabled)
        assertNull(ui.annual)
        assertNull(ui.monthly)
        assertNull(ui.savingsPercent)
        assertNull(ui.trialTerms)
        assertTrue(ui.pricesUnavailable)
        assertEquals(R.string.paywall__header_ai_quota, ui.header)

        answer = CATALOG
        vm.retry()
        assertFalse(vm.ui.value.pricesUnavailable)
        assertTrue(vm.ui.value.ctaEnabled)
    }

    @Test
    fun whileTheCatalogLoadsTheCtaIsDisabledAndCloseStillWorks() {
        val pending = CompletableDeferred<ProCatalog?>()
        val vm = vm(catalog = { pending.await() }).apply { open() }
        assertTrue(vm.ui.value.loading)
        assertFalse(vm.ui.value.ctaEnabled)
        assertFalse(vm.ui.value.pricesUnavailable)
        vm.close()
        assertEquals(1, named("paywall_close").size)
    }

    @Test
    fun closeLogsTheSecondsBucketOfTheElapsedTimeOnce() {
        val vm = vm(PaywallTrigger.WIN_BACK).apply { open() }
        now += 12_000
        vm.close()
        vm.close()
        assertEquals(
            mapOf<String, Any>("trigger" to "win_back", "seconds_bucket" to "5-15"),
            named("paywall_close").single().params,
        )
    }

    @Test
    fun openLogsTheViewWithItsTriggerOnce() {
        val vm = vm(PaywallTrigger.ONBOARDING)
        vm.open()
        vm.open()
        assertEquals(mapOf<String, Any>("trigger" to "onboarding"), named("paywall_view").single().params)
    }

    @Test
    fun openRefreshesTheEntitlementOnce() {
        val vm = vm()
        vm.open()
        vm.open()
        assertEquals(listOf(RefreshReason.PAYWALL_OPEN), refreshes)
    }

    @Test
    fun aFailingRefreshStillLoadsThePrices() {
        val vm = PaywallViewModel(
            PaywallTrigger.REMOVE_ADS, { CATALOG }, { error("offline") }, { false }, pro, FakeStrings,
            { events += it }, { now }, scope,
        )
        vm.open()
        assertTrue(vm.ui.value.ctaEnabled)
    }

    @Test
    fun purchaseLaunchesTheSelectedOffer() {
        val launched = mutableListOf<ProOffer>()
        val vm = vm().apply { open() }
        vm.purchase { launched += it }
        vm.select(Plan.MONTHLY)
        vm.purchase { launched += it }
        assertEquals(listOf(CATALOG.annual, CATALOG.monthly), launched)
    }

    @Test
    fun noPurchaseWithoutPrices() {
        val launched = mutableListOf<ProOffer>()
        vm(catalog = { null }).apply { open() }.purchase { launched += it }
        assertTrue(launched.isEmpty())
    }

    @Test
    fun activatingShowsUntilProThenTheScreenIsPro() {
        val vm = vm().apply { open() }
        pro.value = pro.value.copy(activating = true)
        assertTrue(vm.ui.value.activating)
        assertFalse(vm.ui.value.ctaEnabled)
        pro.value = ProUiState(isPro = true, SubState.ACTIVE, now + 1, activating = false)
        assertTrue(vm.ui.value.isPro)
        assertFalse(vm.ui.value.activating)
        assertFalse(vm.ui.value.ctaEnabled)
    }

    @Test
    fun restoreWithNothingFoundSaysSo() {
        val vm = vm(restore = { false }).apply { open() }
        vm.restore()
        assertTrue(vm.ui.value.restoreNotFound)
        assertFalse(vm.ui.value.isPro)
    }

    @Test
    fun theHeaderFollowsTheTrigger() {
        assertEquals(R.string.paywall__header_remove_ads, vm(PaywallTrigger.REMOVE_ADS).ui.value.header)
        assertEquals(R.string.paywall__header_win_back, vm(PaywallTrigger.WIN_BACK).ui.value.header)
        for (t in PaywallTrigger.entries) assertEquals(t.wire, t.param.value)
    }

    companion object {
        val CATALOG = ProCatalog(
            monthly = ProOffer(Plan.MONTHLY, "monthly", "tok-m", "$2.99", 2_990_000, "USD", "P1M", null),
            annual = ProOffer(Plan.ANNUAL, "annual", "tok-a", "$19.99", 19_990_000, "USD", "P1Y", 7),
        )
    }
}
