/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.billing

import android.app.Activity
import android.app.Application
import com.manjul.aikeyboard.core.ai.kb.EntitlementError
import com.manjul.aikeyboard.core.ai.kb.EntitlementResult
import com.manjul.aikeyboard.core.ai.kb.SubState
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.billing.BillingFixtures.DAY_MS
import com.manjul.aikeyboard.core.billing.BillingFixtures.ok
import com.manjul.aikeyboard.core.billing.BillingFixtures.purchase
import com.manjul.aikeyboard.core.billing.BillingFixtures.signed
import com.manjul.aikeyboard.core.datastore.FeatureSettings
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EntitlementRepositoryTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val dispatcher = UnconfinedTestDispatcher()
    private val store = FeatureSettingsStore(app, dispatcher)
    private val gateway = FakeBillingGateway()
    private val api = FakeEntitlementApi()
    private val sink = RecordingSink()
    private val nonFatal = RecordingNonFatal()
    private val retries = FakeRetryScheduler()
    private val activity: Activity by lazy { Robolectric.buildActivity(Activity::class.java).get() }

    /** Fixed wall clock for the cache, virtual time on top of it for retries. */
    private val base = 1_800_000_000_000L
    private val future = base + 30 * DAY_MS

    private fun TestScope.repo(): EntitlementRepository = EntitlementRepository(
        gateway = gateway,
        client = api,
        signature = BillingFixtures.signature,
        settings = store,
        analytics = sink,
        clock = { base + testScheduler.currentTime },
        scope = backgroundScope,
        nonFatal = nonFatal,
        retryScheduler = retries,
    )

    @Test
    fun `a verified purchase makes isPro true at once and writes the cache`() = runTest(dispatcher) {
        val repo = repo()
        api.queue += ok(signed(true, SubState.ACTIVE, future, issuedAtMs = base))
        repo.purchase(activity, gateway.catalog!!.monthly)
        gateway.emit(PurchaseUpdate.Purchased(listOf(purchase("tok-1"))))
        runCurrent()
        assertTrue(repo.isPro.value)
        assertEquals(ProUiState(true, SubState.ACTIVE, future, activating = false), repo.state.value)
        assertEquals(listOf("tok-1" to ProCatalog.PRODUCT_ID), api.verifyCalls)
        val s = store.flow.first()
        assertTrue(s.proActive)
        assertEquals("active", s.proState)
        assertEquals(future, s.proExpiresAt)
        assertEquals(base, s.proIssuedAt)
        assertTrue(s.proSignature.isNotEmpty())
        assertEquals(base, s.proCheckedAt)
    }

    @Test
    fun `a pending purchase is not verified and stays free`() = runTest(dispatcher) {
        val repo = repo()
        repo.purchase(activity, gateway.catalog!!.monthly)
        gateway.emit(PurchaseUpdate.Purchased(listOf(purchase("tok-p", purchased = false))))
        runCurrent()
        assertFalse(repo.isPro.value)
        assertEquals(emptyList(), api.verifyCalls)
        assertFalse(store.current.proActive)
    }

    @Test
    fun `offline after a purchase shows activating and retries at 2, 4 and 8 s`() = runTest(dispatcher) {
        val repo = repo()
        val p = purchase("tok-1")
        gateway.purchases = listOf(p)
        repeat(3) { api.queue += EntitlementResult.Err(EntitlementError.OFFLINE) }
        api.queue += ok(signed(true, SubState.ACTIVE, future))
        repo.purchase(activity, gateway.catalog!!.monthly)
        gateway.emit(PurchaseUpdate.Purchased(listOf(p)))
        runCurrent()
        assertEquals(1, api.verifyCalls.size)
        assertTrue(repo.state.value.activating)
        assertFalse(repo.isPro.value)
        assertEquals(1, retries.scheduled)

        advanceTimeBy(1_999); runCurrent()
        assertEquals(1, api.verifyCalls.size)
        advanceTimeBy(1); runCurrent()
        assertEquals(2, api.verifyCalls.size)
        advanceTimeBy(4_000); runCurrent()
        assertEquals(3, api.verifyCalls.size)
        assertTrue(repo.state.value.activating)
        advanceTimeBy(8_000); runCurrent()
        assertEquals(4, api.verifyCalls.size)
        assertTrue(repo.isPro.value)
        assertFalse(repo.state.value.activating)
        assertEquals(1, retries.cancelled)
    }

    @Test
    fun `retries stop after 15 minutes and leave the rest to the worker`() = runTest(dispatcher) {
        val repo = repo()
        gateway.purchases = listOf(purchase())
        api.fallback = EntitlementResult.Err(EntitlementError.UNAVAILABLE)
        gateway.emit(PurchaseUpdate.Purchased(listOf(purchase())))
        runCurrent()
        advanceTimeBy(15 * 60 * 1000L); runCurrent()
        val calls = api.verifyCalls.size
        advanceTimeBy(60 * 60 * 1000L); runCurrent()
        assertEquals(calls, api.verifyCalls.size)
        assertTrue(calls in 2..10, "calls=$calls")
        assertEquals(0, retries.cancelled)
    }

    @Test
    fun `offline keeps an existing cache`() = runTest(dispatcher) {
        writeCache(proActive = true, expiresAt = future)
        val repo = repo()
        api.fallback = EntitlementResult.Err(EntitlementError.OFFLINE)
        repo.refresh(RefreshReason.COLD_START)
        assertTrue(repo.isPro.value)
        assertTrue(store.current.proActive)
        assertFalse(repo.state.value.activating)
    }

    @Test
    fun `a bad signature writes free and reports a non-fatal`() = runTest(dispatcher) {
        writeCache(proActive = true, expiresAt = future)
        val repo = repo()
        api.fallback = ok(signed(true, SubState.ACTIVE, future).copy(expiresAtMs = future + DAY_MS))
        repo.refresh(RefreshReason.COLD_START)
        assertFalse(repo.isPro.value)
        assertFalse(store.current.proActive)
        assertEquals(1, nonFatal.reports.size)
        assertEquals("billing", nonFatal.reports.single().first)
    }

    @Test
    fun `invalid_token writes free and logs purchase_fail invalid_token`() = runTest(dispatcher) {
        writeCache(proActive = true, expiresAt = future)
        gateway.purchases = listOf(purchase())
        val repo = repo()
        api.fallback = EntitlementResult.Err(EntitlementError.INVALID_TOKEN)
        repo.refresh(RefreshReason.COLD_START)
        assertFalse(repo.isPro.value)
        assertFalse(store.current.proActive)
        assertEquals(listOf("invalid_token"), sink.named("purchase_fail").map { it.params["code"] })
    }

    @Test
    fun `a cache 3 days and 1 ms past expiry is free`() = runTest(dispatcher) {
        writeCache(proActive = true, expiresAt = base - FeatureSettings.PRO_CACHE_GRACE_MS - 1)
        assertFalse(repo().isPro.value)
    }

    @Test
    fun `a cache inside the 3 day grace is still pro`() = runTest(dispatcher) {
        writeCache(proActive = true, expiresAt = base - FeatureSettings.PRO_CACHE_GRACE_MS + 1)
        assertTrue(repo().isPro.value)
    }

    @Test
    fun `no local purchase with server active is pro, the new device restore case`() = runTest(dispatcher) {
        val repo = repo()
        api.fallback = ok(signed(true, SubState.ACTIVE, future))
        assertTrue(repo.restore())
        assertTrue(repo.isPro.value)
        assertEquals(1, api.currentCalls)
        assertEquals(emptyList(), api.verifyCalls)
        assertEquals(listOf(mapOf<String, Any>("found" to true)), sink.named("restore_purchases").map { it.params })
    }

    @Test
    fun `restore with nothing found logs found false`() = runTest(dispatcher) {
        val repo = repo()
        api.fallback = ok(signed(false, SubState.NONE, 0L))
        assertFalse(repo.restore())
        assertEquals(listOf(mapOf<String, Any>("found" to false)), sink.named("restore_purchases").map { it.params })
    }

    @Test
    fun `resume refreshes at most once per 5 minutes`() = runTest(dispatcher) {
        val repo = repo()
        api.fallback = ok(signed(false, SubState.NONE, 0L))
        repo.refresh(RefreshReason.RESUME)
        repo.refresh(RefreshReason.RESUME)
        assertEquals(1, api.currentCalls)
        advanceTimeBy(5 * 60 * 1000L - 1)
        repo.refresh(RefreshReason.RESUME)
        assertEquals(1, api.currentCalls)
        advanceTimeBy(1)
        repo.refresh(RefreshReason.RESUME)
        assertEquals(2, api.currentCalls)
        repo.refresh(RefreshReason.PAYWALL_OPEN)
        assertEquals(3, api.currentCalls)
    }

    @Test
    fun `purchase events fire once with plan and currency`() = runTest(dispatcher) {
        val repo = repo()
        api.fallback = ok(signed(true, SubState.ACTIVE, future))
        repo.purchase(activity, gateway.catalog!!.monthly)
        gateway.emit(PurchaseUpdate.Purchased(listOf(purchase())))
        runCurrent()
        assertEquals(listOf(gateway.catalog!!.monthly), gateway.launched)
        assertEquals(listOf(mapOf<String, Any>("plan" to "monthly")), sink.named("purchase_start").map { it.params })
        assertEquals(
            listOf(mapOf<String, Any>("plan" to "monthly", "price_tier" to "USD")),
            sink.named("purchase_success").map { it.params },
        )
        assertEquals(emptyList(), sink.named("trial_start"))
        assertEquals(listOf("active"), sink.named("subscription_state").map { it.params["state"] })
        assertEquals(EventCatalog.USER_PROPERTY_PRO_STATUS to "pro", sink.userProperties.last())
    }

    @Test
    fun `a trial offer logs trial_start instead of purchase_success`() = runTest(dispatcher) {
        val repo = repo()
        api.fallback = ok(signed(true, SubState.ACTIVE, future))
        repo.purchase(activity, gateway.catalog!!.annual)
        gateway.emit(PurchaseUpdate.Purchased(listOf(purchase())))
        runCurrent()
        assertEquals(listOf(mapOf<String, Any>("plan" to "annual")), sink.named("trial_start").map { it.params })
        assertEquals(emptyList(), sink.named("purchase_success"))
        assertEquals(EventCatalog.USER_PROPERTY_PRO_STATUS to "trial", sink.userProperties.last())
    }

    @Test
    fun `cancel and failure log purchase_fail with the response code name`() = runTest(dispatcher) {
        repo()
        gateway.emit(PurchaseUpdate.Cancelled)
        gateway.emit(PurchaseUpdate.Failed("ITEM_ALREADY_OWNED"))
        gateway.emit(PurchaseUpdate.Failed("SOMETHING_NEW"))
        runCurrent()
        assertEquals(
            listOf("user_canceled", "item_already_owned", "error"),
            sink.named("purchase_fail").map { it.params["code"] },
        )
    }

    @Test
    fun `subscription_state fires only on a change and grace sets pro_status grace`() = runTest(dispatcher) {
        val repo = repo()
        api.queue += ok(signed(true, SubState.ACTIVE, future))
        api.queue += ok(signed(true, SubState.ACTIVE, future))
        api.queue += ok(signed(true, SubState.GRACE, future))
        repo.refresh(RefreshReason.COLD_START)
        repo.refresh(RefreshReason.PAYWALL_OPEN)
        repo.refresh(RefreshReason.PAYWALL_OPEN)
        assertEquals(listOf("active", "grace"), sink.named("subscription_state").map { it.params["state"] })
        assertEquals(EventCatalog.USER_PROPERTY_PRO_STATUS to "grace", sink.userProperties.last())
    }

    @Test
    fun `a launch that cannot start logs purchase_start only`() = runTest(dispatcher) {
        val repo = repo()
        gateway.launchResult = false
        repo.purchase(activity, gateway.catalog!!.monthly)
        assertEquals(1, sink.named("purchase_start").size)
        assertEquals(listOf("error"), sink.named("purchase_fail").map { it.params["code"] })
    }

    private suspend fun writeCache(proActive: Boolean, expiresAt: Long) {
        store.update {
            it.copy(proActive = proActive, proState = if (proActive) "active" else "none", proExpiresAt = expiresAt)
        }
    }
}
