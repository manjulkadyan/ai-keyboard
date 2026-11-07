/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.billing

import android.app.Activity
import kotlinx.coroutines.flow.Flow

/**
 * The Play Billing surface the app needs (TRD §4.12). [PlayBillingGateway] is the real one. A local purchase never
 * unlocks Pro by itself: [EntitlementRepository] sends every token to kb-api (ARCH-006).
 */
interface BillingGateway {
    /** The `pro` subscription with its `monthly` and `annual` base plans, or null when Play cannot answer. */
    suspend fun catalog(): ProCatalog?

    /** Starts the Play purchase sheet. False when it cannot start (not connected, no catalog yet). */
    fun launch(activity: Activity, offer: ProOffer): Boolean

    /** The subscriptions Play knows for this account, purchased or pending. */
    suspend fun activePurchases(): List<PlayPurchase>

    /** Results of [launch] and purchases that change outside the app (pending ones that complete). */
    val updates: Flow<PurchaseUpdate>
}

/**
 * One Play purchase. [token] is a credential (OPS-001): [toString] leaves it out, and it only goes to kb-api.
 * The order id is not kept at all.
 */
data class PlayPurchase(
    val token: String,
    val productIds: List<String>,
    val purchased: Boolean,
    val acknowledged: Boolean,
) {
    override fun toString(): String =
        "PlayPurchase(products=$productIds, purchased=$purchased, acknowledged=$acknowledged)"
}

sealed interface PurchaseUpdate {
    data class Purchased(val purchases: List<PlayPurchase>) : PurchaseUpdate

    data object Cancelled : PurchaseUpdate

    /** [code] is the `BillingResponseCode` constant name, never the Play debug message. */
    data class Failed(val code: String) : PurchaseUpdate
}
