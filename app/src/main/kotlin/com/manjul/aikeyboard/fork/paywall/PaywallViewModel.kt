/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.paywall

import androidx.annotation.StringRes
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.billing.Plan
import com.manjul.aikeyboard.core.billing.ProCatalog
import com.manjul.aikeyboard.core.billing.ProOffer
import com.manjul.aikeyboard.core.billing.ProUiState
import com.manjul.aikeyboard.core.billing.RefreshReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the paywall shows (PRD FR-29). Prices come only from Play's `ProductDetails` ([annual], [monthly]); with no
 * catalog both are null and the CTA is off. The fields after [activating] add the loading, error, restore and Pro
 * states to the plan's interface; they all have defaults.
 */
data class PaywallUi(
    @StringRes val header: Int,
    val annual: ProOffer?,
    val monthly: ProOffer?,
    val selected: Plan,
    val savingsPercent: Int?,
    val trialTerms: String?,
    val ctaEnabled: Boolean,
    val activating: Boolean,
    val loading: Boolean = false,
    val pricesUnavailable: Boolean = false,
    val isPro: Boolean = false,
    /** Pro arrived while the paywall was open (a purchase or a restore): the screen says "You are Pro" and closes. */
    val becamePro: Boolean = false,
    val restoring: Boolean = false,
    val restoreNotFound: Boolean = false,
) {
    val selectedOffer: ProOffer? get() = if (selected == Plan.ANNUAL) annual else monthly

    companion object {
        /** The first frame: header and close button, no prices yet. */
        fun loading(trigger: PaywallTrigger) = PaywallUi(
            header = trigger.header, annual = null, monthly = null, selected = Plan.ANNUAL, savingsPercent = null,
            trialTerms = null, ctaEnabled = false, activating = false, loading = true,
        )
    }
}

/** The plain-text terms next to the CTA. The app reads them from `strings_fork.xml`; tests use fixed text. */
interface PaywallStrings {
    /** "7 days free, then $19.99/year. Cancel anytime in Google Play." */
    fun trial(days: Int, yearlyPrice: String): String

    /** The annual renewal terms without a trial. */
    fun yearly(price: String): String

    /** The monthly renewal terms (monthly has no trial). */
    fun monthly(price: String): String
}

/**
 * The paywall's state (S4 plan Task 5). [open] logs `paywall_view{trigger}` once, asks kb-api for the entitlement
 * (`refresh(PAYWALL_OPEN)`) and loads Play's catalog. The two run apart, so an offline kb-api never holds up the
 * prices. [close] logs `paywall_close{trigger, seconds_bucket}` once. Pro is decided by kb-api only: [pro] is the
 * repository's state, which changes only after a signed answer (ARCH-006).
 */
class PaywallViewModel(
    private val trigger: PaywallTrigger,
    private val catalog: suspend () -> ProCatalog?,
    private val refresh: suspend (RefreshReason) -> Unit,
    private val restore: suspend () -> Boolean,
    private val pro: StateFlow<ProUiState>,
    private val strings: PaywallStrings,
    private val analytics: (AnalyticsEvent) -> Unit,
    private val clock: () -> Long,
    private val scope: CoroutineScope,
) {
    private val _ui = MutableStateFlow(PaywallUi.loading(trigger).withPro(pro.value, wasPro = pro.value.isPro))
    val ui: StateFlow<PaywallUi> = _ui.asStateFlow()

    private var openedAt: Long? = null
    private var closed = false
    private var loadJob: Job? = null
    private val proAtOpen = pro.value.isPro

    init {
        scope.launch { pro.collect { state -> _ui.update { it.withPro(state, proAtOpen) } } }
    }

    fun open() {
        if (openedAt != null) return
        openedAt = clock()
        analytics(EventCatalog.paywallView(trigger.param))
        scope.launch {
            try {
                refresh(RefreshReason.PAYWALL_OPEN)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // kb-api out of reach: the cached Pro state stands; the prices still load.
            }
        }
        load()
    }

    /** "Try again" after the catalog did not load. */
    fun retry() {
        if (loadJob?.isActive == true) return
        load()
    }

    fun select(plan: Plan) = _ui.update { it.copy(selected = plan).withPrices() }

    /** Starts Play's purchase sheet for the selected plan; [launch] calls `EntitlementRepository.purchase`. */
    fun purchase(launch: (ProOffer) -> Unit) {
        val ui = _ui.value
        if (!ui.ctaEnabled) return
        launch(ui.selectedOffer ?: return)
    }

    fun restore() {
        if (_ui.value.restoring) return
        _ui.update { it.copy(restoring = true, restoreNotFound = false) }
        scope.launch {
            val found = try {
                restore.invoke()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            _ui.update { it.copy(restoring = false, restoreNotFound = !found && !it.isPro) }
        }
    }

    /** The close button and Back. Logs once. */
    fun close() {
        if (closed) return
        closed = true
        val seconds = ((clock() - (openedAt ?: clock())) / 1000).coerceAtLeast(0)
        analytics(EventCatalog.paywallClose(trigger.param, seconds))
    }

    private fun load() {
        _ui.update { it.copy(loading = true, pricesUnavailable = false).withPrices() }
        loadJob = scope.launch {
            val c = try {
                catalog()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            _ui.update {
                it.copy(
                    annual = c?.annual,
                    monthly = c?.monthly,
                    savingsPercent = c?.annualSavingsPercent,
                    loading = false,
                    pricesUnavailable = c == null,
                ).withPrices()
            }
        }
    }

    private fun PaywallUi.withPro(state: ProUiState, wasPro: Boolean) = copy(
        isPro = state.isPro,
        becamePro = becamePro || (state.isPro && !wasPro),
        activating = state.activating && !state.isPro,
        restoreNotFound = restoreNotFound && !state.isPro,
    ).withPrices()

    /** Derives the terms and the CTA from the offers and the Pro state. */
    private fun PaywallUi.withPrices(): PaywallUi {
        val offer = selectedOffer
        val trialDays = offer?.trialDays
        val terms = when {
            offer == null -> null
            offer.plan == Plan.ANNUAL && trialDays != null -> strings.trial(trialDays, offer.formattedPrice)
            offer.plan == Plan.ANNUAL -> strings.yearly(offer.formattedPrice)
            else -> strings.monthly(offer.formattedPrice)
        }
        return copy(trialTerms = terms, ctaEnabled = offer != null && !loading && !activating && !isPro)
    }
}
