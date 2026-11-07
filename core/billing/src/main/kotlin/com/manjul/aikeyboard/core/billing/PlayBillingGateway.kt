/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [BillingGateway] on Play Billing Library 9. The client never acknowledges a purchase: kb-api does that after it
 * verifies the token (ARCH-006). Nothing here logs. A failure maps to its `BillingResponseCode` name only.
 */
class PlayBillingGateway(context: Context) : BillingGateway {
    private val _updates = MutableSharedFlow<PurchaseUpdate>(extraBufferCapacity = 8)
    override val updates: Flow<PurchaseUpdate> = _updates.asSharedFlow()

    private val listener = PurchasesUpdatedListener { result, purchases ->
        val update = when (result.responseCode) {
            BillingResponseCode.OK -> PurchaseUpdate.Purchased(purchases.orEmpty().map(::toPlayPurchase))
            BillingResponseCode.USER_CANCELED -> PurchaseUpdate.Cancelled
            else -> PurchaseUpdate.Failed(codeName(result.responseCode))
        }
        _updates.tryEmit(update)
    }

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(listener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    @Volatile private var details: ProductDetails? = null

    override suspend fun catalog(): ProCatalog? {
        if (!connect()) return null
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(ProCatalog.PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build(),
            ),
        ).build()
        val result = client.queryProductDetails(params)
        if (result.billingResult.responseCode != BillingResponseCode.OK) return null
        val product = result.productDetailsList?.firstOrNull { it.productId == ProCatalog.PRODUCT_ID } ?: return null
        details = product
        val offers = product.subscriptionOfferDetails.orEmpty()
        fun best(plan: Plan): ProOffer? {
            val forPlan = offers.filter { it.basePlanId == plan.wire }.mapNotNull { o ->
                val phases = o.pricingPhases.pricingPhaseList.map {
                    PricePhase(it.priceAmountMicros, it.formattedPrice, it.priceCurrencyCode, it.billingPeriod,
                        it.billingCycleCount)
                }
                ProOffer.from(plan, o.basePlanId, o.offerToken, phases)
            }
            // A trial offer first (Play only lists offers this account can still use), then the base plan.
            return forPlan.firstOrNull { it.trialDays != null } ?: forPlan.firstOrNull()
        }
        return ProCatalog(best(Plan.MONTHLY) ?: return null, best(Plan.ANNUAL) ?: return null)
    }

    override fun launch(activity: Activity, offer: ProOffer): Boolean {
        val product = details ?: return false
        if (!client.isReady) return false
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(product)
                    .setOfferToken(offer.offerToken)
                    .build(),
            ),
        ).build()
        return client.launchBillingFlow(activity, params).responseCode == BillingResponseCode.OK
    }

    override suspend fun activePurchases(): List<PlayPurchase> {
        if (!connect()) return emptyList()
        val result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        )
        if (result.billingResult.responseCode != BillingResponseCode.OK) return emptyList()
        return result.purchasesList.map(::toPlayPurchase)
    }

    private suspend fun connect(): Boolean {
        if (client.isReady) return true
        return withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val done = CompletableDeferred<Unit>()
                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (done.complete(Unit) && cont.isActive) {
                            cont.resume(result.responseCode == BillingResponseCode.OK)
                        }
                    }

                    override fun onBillingServiceDisconnected() = Unit
                })
            }
        } ?: false
    }

    private fun toPlayPurchase(p: Purchase) = PlayPurchase(
        token = p.purchaseToken,
        productIds = p.products,
        purchased = p.purchaseState == Purchase.PurchaseState.PURCHASED,
        acknowledged = p.isAcknowledged,
    )

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000L

        private val CODE_NAMES: Map<Int, String> = mapOf(
            BillingResponseCode.FEATURE_NOT_SUPPORTED to "FEATURE_NOT_SUPPORTED",
            BillingResponseCode.SERVICE_DISCONNECTED to "SERVICE_DISCONNECTED",
            BillingResponseCode.OK to "OK",
            BillingResponseCode.USER_CANCELED to "USER_CANCELED",
            BillingResponseCode.SERVICE_UNAVAILABLE to "SERVICE_UNAVAILABLE",
            BillingResponseCode.BILLING_UNAVAILABLE to "BILLING_UNAVAILABLE",
            BillingResponseCode.ITEM_UNAVAILABLE to "ITEM_UNAVAILABLE",
            BillingResponseCode.DEVELOPER_ERROR to "DEVELOPER_ERROR",
            BillingResponseCode.ERROR to "ERROR",
            BillingResponseCode.ITEM_ALREADY_OWNED to "ITEM_ALREADY_OWNED",
            BillingResponseCode.ITEM_NOT_OWNED to "ITEM_NOT_OWNED",
            BillingResponseCode.NETWORK_ERROR to "NETWORK_ERROR",
        )

        fun codeName(code: Int): String = CODE_NAMES[code] ?: "ERROR"
    }
}
