/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.billing

import android.app.Activity
import com.manjul.aikeyboard.core.ai.kb.Entitlement
import com.manjul.aikeyboard.core.ai.kb.EntitlementApi
import com.manjul.aikeyboard.core.ai.kb.EntitlementError
import com.manjul.aikeyboard.core.ai.kb.EntitlementResult
import com.manjul.aikeyboard.core.ai.kb.SubState
import com.manjul.aikeyboard.core.analytics.AnalyticsSink
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.analytics.ProPlanParam
import com.manjul.aikeyboard.core.analytics.ProStatus
import com.manjul.aikeyboard.core.analytics.PurchaseFailCode
import com.manjul.aikeyboard.core.analytics.SubscriptionStateParam
import com.manjul.aikeyboard.core.common.NonFatalReporter
import com.manjul.aikeyboard.core.common.NoopNonFatalReporter
import com.manjul.aikeyboard.core.datastore.FeatureSettings
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.core.datastore.isProAt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ProUiState(val isPro: Boolean, val state: SubState, val expiresAtMs: Long, val activating: Boolean)

enum class RefreshReason { COLD_START, RESUME, PAYWALL_OPEN, PURCHASE }

/** Carries a failed verify across process restarts. The real one is [WorkManagerVerifyScheduler]. */
interface VerifyRetryScheduler {
    fun schedule()

    fun cancel()
}

/**
 * The single source of Pro state on the device (TRD §4.12, ARCH-006). Every Play purchase token goes to kb-api.
 * Only a verdict with a valid [EntitlementSignature] reaches [FeatureSettings]. Pro holds until `expires_at` plus
 * [FeatureSettings.PRO_CACHE_GRACE_MS], so the keyboard keeps working offline.
 *
 * Nothing here logs. Tokens stay inside the calls to [EntitlementApi.verify] (OPS-001).
 */
class EntitlementRepository(
    private val gateway: BillingGateway,
    private val client: EntitlementApi,
    private val signature: EntitlementSignature,
    private val settings: FeatureSettingsStore,
    private val analytics: AnalyticsSink,
    private val clock: () -> Long,
    private val scope: CoroutineScope,
    private val nonFatal: NonFatalReporter = NoopNonFatalReporter,
    private val retryScheduler: VerifyRetryScheduler? = null,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(uiStateOf(settings.current, activating = false))
    private val _isPro = MutableStateFlow(_state.value.isPro)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()
    val state: StateFlow<ProUiState> = _state.asStateFlow()

    private var lastState: SubState = _state.value.state
    private var lastStatus: ProStatus? = null
    private var lastResumeRefreshAt: Long? = null
    private var pendingOffer: ProOffer? = null
    private var trial = false
    private var retryJob: Job? = null
    private var retryScheduled = false

    init {
        publishStatus()
        scope.launch { gateway.updates.collect(::onUpdate) }
    }

    suspend fun refresh(reason: RefreshReason) {
        if (reason == RefreshReason.RESUME) {
            val now = clock()
            val last = lastResumeRefreshAt
            if (last != null && now - last < RESUME_THROTTLE_MS) return
            lastResumeRefreshAt = now
        }
        sync(gateway.activePurchases(), afterPurchase = reason == RefreshReason.PURCHASE)
    }

    /** Asks kb-api again, also with no local purchase (RTDN can know a purchase made on another device). */
    suspend fun restore(): Boolean {
        sync(gateway.activePurchases(), afterPurchase = false)
        val found = _isPro.value
        analytics.log(EventCatalog.restorePurchases(found))
        return found
    }

    fun purchase(activity: Activity, offer: ProOffer) {
        analytics.log(EventCatalog.purchaseStart(offer.plan.param))
        pendingOffer = offer
        if (!gateway.launch(activity, offer)) {
            pendingOffer = null
            analytics.log(EventCatalog.purchaseFail(PurchaseFailCode.ERROR))
        }
    }

    /** One [VerifyPurchaseWorker] run. True when done, false when kb-api is still out of reach. */
    internal suspend fun syncFromWorker(): Boolean =
        sync(gateway.activePurchases(), afterPurchase = true, fromWorker = true) != Sync.RETRY

    private suspend fun onUpdate(update: PurchaseUpdate) {
        when (update) {
            is PurchaseUpdate.Purchased -> if (update.purchases.any(::isProPurchase)) {
                sync(update.purchases, afterPurchase = true)
            }
            PurchaseUpdate.Cancelled -> fail(PurchaseFailCode.USER_CANCELED)
            is PurchaseUpdate.Failed -> fail(PurchaseFailCode.fromResponseCodeName(update.code))
        }
    }

    private fun fail(code: PurchaseFailCode) {
        pendingOffer = null
        analytics.log(EventCatalog.purchaseFail(code))
    }

    private enum class Sync { DONE, RETRY }

    private suspend fun sync(purchases: List<PlayPurchase>, afterPurchase: Boolean, fromWorker: Boolean = false): Sync {
        val outcome = mutex.withLock {
            val tokens = purchases.filter(::isProPurchase).map { it.token }.distinct()
            val results = if (tokens.isEmpty()) {
                listOf(client.current())
            } else {
                tokens.map { client.verify(it, ProCatalog.PRODUCT_ID) }
            }
            val oks = results.filterIsInstance<EntitlementResult.Ok>().map { it.entitlement }
            val errors = results.filterIsInstance<EntitlementResult.Err>().map { it.error }
            when {
                oks.isNotEmpty() -> {
                    apply(oks.firstOrNull { it.proActive } ?: oks.last())
                    Sync.DONE
                }
                EntitlementError.INVALID_TOKEN in errors -> {
                    writeFree()
                    fail(PurchaseFailCode.INVALID_TOKEN)
                    Sync.DONE
                }
                errors.any { it == EntitlementError.OFFLINE || it == EntitlementError.UNAVAILABLE } -> Sync.RETRY
                else -> Sync.DONE
            }
        }
        if (outcome == Sync.DONE) {
            retryJob?.cancel()
            _state.value = _state.value.copy(activating = false)
            if (!fromWorker && retryScheduled) {
                retryScheduled = false
                retryScheduler?.cancel()
            }
        } else if (afterPurchase && !fromWorker) {
            _state.value = _state.value.copy(activating = true)
            startRetries()
        }
        return outcome
    }

    private fun startRetries() {
        if (retryJob?.isActive == true) return
        if (!retryScheduled) {
            retryScheduled = true
            retryScheduler?.schedule()
        }
        retryJob = scope.launch {
            var wait = FIRST_RETRY_MS
            var waited = 0L
            while (waited + wait <= RETRY_WINDOW_MS) {
                delay(wait)
                waited += wait
                wait *= 2
                if (syncRetry() == Sync.DONE) return@launch
            }
        }
    }

    private suspend fun syncRetry(): Sync {
        val outcome = sync(gateway.activePurchases(), afterPurchase = false)
        if (outcome == Sync.RETRY) _state.value = _state.value.copy(activating = true)
        return outcome
    }

    private suspend fun apply(e: Entitlement) {
        if (!signature.isValid(e)) {
            writeFree()
            nonFatal.report(AREA, InvalidEntitlementSignature())
            return
        }
        val now = clock()
        settings.update {
            it.copy(
                proActive = e.proActive, proState = e.state.wire, proExpiresAt = e.expiresAtMs,
                proIssuedAt = e.issuedAtMs, proSignature = e.signature, proCheckedAt = now,
            )
        }
        val offer = pendingOffer
        if (offer != null && e.proActive) {
            pendingOffer = null
            if (offer.trialDays != null) {
                trial = true
                analytics.log(EventCatalog.trialStart(offer.plan.param))
            } else {
                analytics.log(EventCatalog.purchaseSuccess(offer.plan.param, offer.currency))
            }
        }
        publish()
    }

    private suspend fun writeFree() {
        val now = clock()
        settings.update {
            it.copy(
                proActive = false, proState = SubState.NONE.wire, proExpiresAt = 0L, proIssuedAt = 0L,
                proSignature = "", proCheckedAt = now,
            )
        }
        publish()
    }

    private fun publish() {
        val next = uiStateOf(settings.current, activating = _state.value.activating)
        _state.value = next
        _isPro.value = next.isPro
        if (next.state != lastState) {
            lastState = next.state
            analytics.log(EventCatalog.subscriptionState(SubscriptionStateParam.valueOf(next.state.name)))
        }
        if (!next.isPro || next.state != SubState.ACTIVE) trial = false
        publishStatus()
    }

    private fun publishStatus() {
        val s = _state.value
        val status = when {
            !s.isPro -> ProStatus.FREE
            s.state == SubState.GRACE -> ProStatus.GRACE
            trial -> ProStatus.TRIAL
            else -> ProStatus.PRO
        }
        if (status != lastStatus) {
            lastStatus = status
            analytics.setUserProperty(EventCatalog.USER_PROPERTY_PRO_STATUS, status.value)
        }
    }

    private fun uiStateOf(s: FeatureSettings, activating: Boolean) = ProUiState(
        isPro = s.isProAt(clock()),
        state = SubState.fromWire(s.proState) ?: SubState.NONE,
        expiresAtMs = s.proExpiresAt,
        activating = activating,
    )

    private fun isProPurchase(p: PlayPurchase) = p.purchased && ProCatalog.PRODUCT_ID in p.productIds

    private val Plan.param: ProPlanParam get() = ProPlanParam.valueOf(name)

    /** Carries no entitlement data, so the report holds no signature or time. */
    private class InvalidEntitlementSignature : IllegalStateException("kb-api entitlement signature is invalid")

    companion object {
        const val AREA = "billing"
        const val RESUME_THROTTLE_MS = 5 * 60 * 1000L
        const val FIRST_RETRY_MS = 2_000L
        const val RETRY_WINDOW_MS = 15 * 60 * 1000L
    }
}
