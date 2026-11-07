/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.billing

import android.app.Activity
import android.app.Application
import com.manjul.aikeyboard.core.ai.AuthHeaders
import com.manjul.aikeyboard.core.ai.FakeTokenSource
import com.manjul.aikeyboard.core.ai.TokenState
import com.manjul.aikeyboard.core.ai.kb.EntitlementClient
import com.manjul.aikeyboard.core.ai.kb.EntitlementResult
import com.manjul.aikeyboard.core.ai.kb.SubState
import com.manjul.aikeyboard.core.billing.BillingFixtures.DAY_MS
import com.manjul.aikeyboard.core.billing.BillingFixtures.ok
import com.manjul.aikeyboard.core.billing.BillingFixtures.signed
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/** OPS-001: the purchase token, the order id and the auth header never reach a log, a message or a param. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BillingLogRedactionTest {
    private val token = "tok-SECRET-123"
    private val orderId = "GPA.1234-5678"
    private val secrets = listOf(token, orderId, "Bearer")

    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val dispatcher = UnconfinedTestDispatcher()
    private val http = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()
    private val tokens = FakeTokenSource(
        TokenState.Ready(mapOf(AuthHeaders.APP_CHECK to "ac", AuthHeaders.AUTHORIZATION to "Bearer id-$token")),
    )

    private fun assertClean(what: String, text: String?) {
        secrets.forEach { assertFalse(text.orEmpty().contains(it), "$what leaks $it: $text") }
    }

    @Test
    fun `a full purchase, retry and failure run leaks nothing`() = runTest(dispatcher) {
        ShadowLog.clear()
        val server = MockWebServer().apply { start() }
        val sink = RecordingSink()
        val nonFatal = RecordingNonFatal()
        val purchase = PlayPurchase(token, listOf(ProCatalog.PRODUCT_ID), purchased = true, acknowledged = false)
        val gateway = FakeBillingGateway(purchases = listOf(purchase))
        val future = 1_800_000_000_000L + 30 * DAY_MS
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":"unavailable $token"}"""))
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"pro_active":true,"state":"active","expires_at":$future,"issued_at":1,""" +
                        """"signature":"${signed(true, SubState.ACTIVE, future).signature}"}""",
                ),
            )
            server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_token"}"""))
            val client = EntitlementClient(http, server.url("/").toString(), tokens, Dispatchers.Unconfined)
            val repo = EntitlementRepository(
                gateway, client, BillingFixtures.signature, FeatureSettingsStore(app, dispatcher), sink,
                clock = { 1_800_000_000_000L + testScheduler.currentTime }, scope = backgroundScope,
                nonFatal = nonFatal, retryScheduler = FakeRetryScheduler(),
            )
            val activity = Robolectric.buildActivity(Activity::class.java).get()
            repo.purchase(activity, gateway.catalog!!.annual)
            gateway.emit(PurchaseUpdate.Purchased(listOf(purchase)))
            runCurrent()
            advanceTimeBy(2_000); runCurrent()
            repo.refresh(RefreshReason.PAYWALL_OPEN)
            gateway.emit(PurchaseUpdate.Failed("DEVELOPER_ERROR"))
            runCurrent()

            assertEquals(3, server.requestCount)
            ShadowLog.getLogs().forEach { assertClean("log ${it.tag}", "${it.msg} ${it.throwable}") }
            sink.events.forEach { assertClean("event ${it.name}", it.toString()) }
            sink.userProperties.forEach { assertClean("user property", it.toString()) }
            nonFatal.reports.forEach { (area, e) -> assertClean("non-fatal $area", e.stackTraceToString()) }
            assertClean("purchase", purchase.toString())
            assertClean("update", PurchaseUpdate.Purchased(listOf(purchase)).toString())
            assertClean("state", repo.state.value.toString())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `a refused connection maps to offline without the token in the result`() = runTest(dispatcher) {
        val port = ServerSocket(0).use { it.localPort }
        val client = EntitlementClient(http, "http://127.0.0.1:$port/", tokens, Dispatchers.Unconfined)
        val result = client.verify(token, ProCatalog.PRODUCT_ID)
        assertIs<EntitlementResult.Err>(result)
        assertClean("result", result.toString())
    }

    @Test
    fun `entitlement and ui state toString hide the signature`() {
        val e = signed(true, SubState.ACTIVE, 5L)
        assertFalse(e.toString().contains(e.signature))
        assertEquals(ok(e).entitlement, e)
    }
}
