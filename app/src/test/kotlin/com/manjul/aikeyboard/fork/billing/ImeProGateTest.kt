/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.billing

import android.app.Activity
import android.app.Application
import com.manjul.aikeyboard.core.ai.kb.Entitlement
import com.manjul.aikeyboard.core.ai.kb.EntitlementApi
import com.manjul.aikeyboard.core.ai.kb.EntitlementResult
import com.manjul.aikeyboard.core.ai.kb.SubState
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.AnalyticsSink
import com.manjul.aikeyboard.core.billing.BillingGateway
import com.manjul.aikeyboard.core.billing.EntitlementRepository
import com.manjul.aikeyboard.core.billing.EntitlementSignature
import com.manjul.aikeyboard.core.billing.PlayPurchase
import com.manjul.aikeyboard.core.billing.ProCatalog
import com.manjul.aikeyboard.core.billing.ProOffer
import com.manjul.aikeyboard.core.billing.PurchaseUpdate
import com.manjul.aikeyboard.core.billing.RefreshReason
import com.manjul.aikeyboard.core.datastore.FeatureSettings
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The IME reads Pro only through [FeatureSettingsStore] (S4 plan Task 4). The repository is the only writer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ImeProGateTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val app = RuntimeEnvironment.getApplication()
    private val store = FeatureSettingsStore(app, dispatcher)
    private var now = 1_000_000_000L
    private val gate = proGate(store) { now }

    @After
    fun tearDown() = scope.cancel()

    private fun repository(answer: EntitlementResult) = EntitlementRepository(
        gateway = NoPurchases,
        client = FixedApi(answer),
        signature = EntitlementSignature("", debug = true),
        settings = store,
        analytics = SilentSink,
        clock = { now },
        scope = scope,
    )

    @Test
    fun `the ime gate sees pro within 2 s of the repository writing it`() = runTest {
        assertFalse(gate())
        val entitlement = Entitlement(true, SubState.ACTIVE, now + DAY_MS * 30, now, "sig")
        repository(EntitlementResult.Ok(entitlement)).refresh(RefreshReason.COLD_START)

        val deadline = System.currentTimeMillis() + 2_000
        while (!gate() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue(gate())
    }

    @Test
    fun `a lapsed cache is not pro`() = runTest {
        store.update { it.copy(proActive = true, proState = "active", proExpiresAt = now - FeatureSettings.PRO_CACHE_GRACE_MS - 1) }
        assertFalse(gate())
    }

    @Test
    fun `an expired entitlement still inside the offline grace is pro`() = runTest {
        store.update { it.copy(proActive = true, proState = "active", proExpiresAt = now - DAY_MS) }
        assertTrue(gate())
        now += FeatureSettings.PRO_CACHE_GRACE_MS
        assertFalse(gate())
    }

    @Test
    fun `a free verdict turns the gate off`() = runTest {
        store.update { it.copy(proActive = true, proState = "active", proExpiresAt = now + DAY_MS) }
        assertTrue(gate())
        val free = Entitlement(false, SubState.EXPIRED, now - DAY_MS, now, "sig")
        repository(EntitlementResult.Ok(free)).refresh(RefreshReason.RESUME)
        assertFalse(gate())
    }

    private object NoPurchases : BillingGateway {
        override suspend fun catalog(): ProCatalog? = null

        override fun launch(activity: Activity, offer: ProOffer): Boolean = false

        override suspend fun activePurchases(): List<PlayPurchase> = emptyList()

        override val updates: Flow<PurchaseUpdate> = emptyFlow()
    }

    private class FixedApi(private val answer: EntitlementResult) : EntitlementApi {
        override suspend fun verify(purchaseToken: String, productId: String) = answer

        override suspend fun current() = answer
    }

    private object SilentSink : AnalyticsSink {
        override fun log(event: AnalyticsEvent) = Unit
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
