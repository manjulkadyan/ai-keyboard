/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.analytics

import kotlin.test.Test
import kotlin.test.assertEquals

/** PRD §12 billing events: closed values only, never a purchase token, order id or price. */
class BillingEventsTest {
    @Test fun `purchase start, trial start and success carry the plan`() {
        assertEquals(mapOf("plan" to "monthly"), EventCatalog.purchaseStart(ProPlanParam.MONTHLY).params)
        assertEquals("purchase_start", EventCatalog.purchaseStart(ProPlanParam.MONTHLY).name)
        assertEquals(mapOf("plan" to "annual"), EventCatalog.trialStart(ProPlanParam.ANNUAL).params)
        assertEquals("trial_start", EventCatalog.trialStart(ProPlanParam.ANNUAL).name)
        val success = EventCatalog.purchaseSuccess(ProPlanParam.ANNUAL, "PKR")
        assertEquals("purchase_success", success.name)
        assertEquals(mapOf("plan" to "annual", "price_tier" to "PKR"), success.params)
    }

    @Test fun `price tier is an ISO currency code or other`() {
        for (bad in listOf("Rs 1,500", "pkr", "", "GPA.1234-5678", "USDX", "tok-SECRET-123")) {
            assertEquals("other", EventCatalog.purchaseSuccess(ProPlanParam.MONTHLY, bad).params["price_tier"], bad)
        }
        assertEquals("USD", EventCatalog.purchaseSuccess(ProPlanParam.MONTHLY, "USD").params["price_tier"])
    }

    @Test fun `purchase fail, state and restore`() {
        assertEquals(mapOf("code" to "user_canceled"), EventCatalog.purchaseFail(PurchaseFailCode.USER_CANCELED).params)
        assertEquals(mapOf("code" to "invalid_token"), EventCatalog.purchaseFail(PurchaseFailCode.INVALID_TOKEN).params)
        assertEquals("purchase_fail", EventCatalog.purchaseFail(PurchaseFailCode.ERROR).name)
        assertEquals(mapOf("state" to "hold"), EventCatalog.subscriptionState(SubscriptionStateParam.ON_HOLD).params)
        assertEquals("subscription_state", EventCatalog.subscriptionState(SubscriptionStateParam.ON_HOLD).name)
        assertEquals(mapOf("found" to true), EventCatalog.restorePurchases(true).params)
        assertEquals("restore_purchases", EventCatalog.restorePurchases(true).name)
    }

    @Test fun `fail codes map from the Play response code names`() {
        assertEquals(PurchaseFailCode.SERVICE_UNAVAILABLE, PurchaseFailCode.fromResponseCodeName("SERVICE_UNAVAILABLE"))
        assertEquals(PurchaseFailCode.ITEM_ALREADY_OWNED, PurchaseFailCode.fromResponseCodeName("ITEM_ALREADY_OWNED"))
        assertEquals(PurchaseFailCode.USER_CANCELED, PurchaseFailCode.fromResponseCodeName("USER_CANCELED"))
        assertEquals(PurchaseFailCode.ERROR, PurchaseFailCode.fromResponseCodeName("SOMETHING_NEW"))
    }

    @Test fun `pro status values`() {
        assertEquals("pro_status", EventCatalog.USER_PROPERTY_PRO_STATUS)
        assertEquals(listOf("free", "trial", "pro", "grace"), ProStatus.entries.map { it.value })
    }

    @Test fun `subscription states use the kb-api wire strings`() {
        assertEquals(
            listOf("none", "pending", "active", "grace", "hold", "paused", "expired", "revoked"),
            SubscriptionStateParam.entries.map { it.value },
        )
    }
}
