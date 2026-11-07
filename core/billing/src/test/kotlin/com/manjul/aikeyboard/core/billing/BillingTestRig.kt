/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.billing

import com.manjul.aikeyboard.core.ai.kb.Entitlement
import com.manjul.aikeyboard.core.ai.kb.EntitlementApi
import com.manjul.aikeyboard.core.ai.kb.EntitlementError
import com.manjul.aikeyboard.core.ai.kb.EntitlementResult
import com.manjul.aikeyboard.core.ai.kb.SubState
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.AnalyticsSink
import com.manjul.aikeyboard.core.common.NonFatalReporter
import java.security.KeyPair

/** Scripted kb-api: each call takes the next queued result, then repeats [fallback]. */
class FakeEntitlementApi(var fallback: EntitlementResult = EntitlementResult.Err(EntitlementError.UNAVAILABLE)) :
    EntitlementApi {
    val queue = ArrayDeque<EntitlementResult>()
    val verifyCalls = mutableListOf<Pair<String, String>>()
    var currentCalls = 0
        private set

    override suspend fun verify(purchaseToken: String, productId: String): EntitlementResult {
        verifyCalls += purchaseToken to productId
        return queue.removeFirstOrNull() ?: fallback
    }

    override suspend fun current(): EntitlementResult {
        currentCalls++
        return queue.removeFirstOrNull() ?: fallback
    }
}

class RecordingSink : AnalyticsSink {
    val events = mutableListOf<AnalyticsEvent>()
    val userProperties = mutableListOf<Pair<String, String?>>()

    override fun log(event: AnalyticsEvent) {
        events += event
    }

    override fun setUserProperty(name: String, value: String?) {
        userProperties += name to value
    }

    fun named(name: String) = events.filter { it.name == name }
}

class RecordingNonFatal : NonFatalReporter {
    val reports = mutableListOf<Pair<String, Throwable>>()

    override fun report(area: String, error: Throwable) {
        reports += area to error
    }
}

class FakeRetryScheduler : VerifyRetryScheduler {
    var scheduled = 0
        private set
    var cancelled = 0
        private set

    override fun schedule() {
        scheduled++
    }

    override fun cancel() {
        cancelled++
    }
}

object BillingFixtures {
    const val DAY_MS = 24L * 60 * 60 * 1000
    val keys: KeyPair = EntitlementSignatureTest.testKeyPair()
    val signature = EntitlementSignature(EntitlementSignatureTest.publicKeyBase64(keys), debug = false)

    fun signed(proActive: Boolean, state: SubState, expiresAtMs: Long, issuedAtMs: Long = 1L): Entitlement =
        EntitlementSignatureTest.sign(keys, Entitlement(proActive, state, expiresAtMs, issuedAtMs, ""))

    fun ok(e: Entitlement) = EntitlementResult.Ok(e)

    fun purchase(token: String = "tok-1", purchased: Boolean = true, acknowledged: Boolean = false) =
        PlayPurchase(token = token, productIds = listOf(ProCatalog.PRODUCT_ID), purchased = purchased, acknowledged)
}
