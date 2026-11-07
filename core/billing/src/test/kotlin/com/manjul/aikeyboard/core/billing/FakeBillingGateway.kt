/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.billing

import android.app.Activity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** In-memory [BillingGateway]. Tests set [purchases] and push [PurchaseUpdate]s with [emit]. */
class FakeBillingGateway(
    var catalog: ProCatalog? = testCatalog(),
    var purchases: List<PlayPurchase> = emptyList(),
    var launchResult: Boolean = true,
) : BillingGateway {
    private val flow = MutableSharedFlow<PurchaseUpdate>(extraBufferCapacity = 16)
    val launched = mutableListOf<ProOffer>()
    var activePurchaseCalls = 0
        private set

    override suspend fun catalog(): ProCatalog? = catalog

    override fun launch(activity: Activity, offer: ProOffer): Boolean {
        launched += offer
        return launchResult
    }

    override suspend fun activePurchases(): List<PlayPurchase> {
        activePurchaseCalls++
        return purchases
    }

    override val updates: Flow<PurchaseUpdate> get() = flow

    suspend fun emit(update: PurchaseUpdate) = flow.emit(update)

    companion object {
        fun offer(plan: Plan, trialDays: Int? = null, micros: Long = 2_990_000L, currency: String = "USD") = ProOffer(
            plan = plan,
            basePlanId = plan.wire,
            offerToken = "offer-${plan.wire}",
            formattedPrice = "$2.99",
            priceMicros = micros,
            currency = currency,
            billingPeriod = if (plan == Plan.MONTHLY) "P1M" else "P1Y",
            trialDays = trialDays,
        )

        fun testCatalog() = ProCatalog(
            monthly = offer(Plan.MONTHLY),
            annual = offer(Plan.ANNUAL, trialDays = 7, micros = 19_990_000L),
        )
    }
}
