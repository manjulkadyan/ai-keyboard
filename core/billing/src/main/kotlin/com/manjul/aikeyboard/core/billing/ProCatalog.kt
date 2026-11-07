/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.billing

import kotlin.math.roundToInt

enum class Plan(val wire: String) { MONTHLY("monthly"), ANNUAL("annual") }

/** One pricing phase of a Play offer. [billingPeriod] is ISO-8601, for example `P7D` or `P1M`. */
data class PricePhase(
    val priceMicros: Long,
    val formattedPrice: String,
    val currency: String,
    val billingPeriod: String,
    val cycles: Int = 1,
)

/** A purchasable offer. Price fields are the recurring paid phase. [trialDays] is the free phase, if any. */
data class ProOffer(
    val plan: Plan,
    val basePlanId: String,
    val offerToken: String,
    val formattedPrice: String,
    val priceMicros: Long,
    val currency: String,
    val billingPeriod: String,
    val trialDays: Int?,
) {
    companion object {
        /**
         * Builds an offer from its pricing phases. The trial comes from a free phase, never from the offer id.
         * Null when the offer has no paid phase.
         */
        fun from(plan: Plan, basePlanId: String, offerToken: String, phases: List<PricePhase>): ProOffer? {
            val paid = phases.lastOrNull { it.priceMicros > 0 } ?: return null
            val trialDays = phases.firstOrNull { it.priceMicros == 0L }
                ?.let { free -> isoDays(free.billingPeriod)?.times(free.cycles.coerceAtLeast(1)) }
                ?.takeIf { it > 0 }
            return ProOffer(
                plan, basePlanId, offerToken, paid.formattedPrice, paid.priceMicros, paid.currency,
                paid.billingPeriod, trialDays,
            )
        }

        private val ISO_PERIOD = Regex("""^P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)W)?(?:(\d+)D)?$""")

        internal fun isoDays(period: String): Int? {
            val m = ISO_PERIOD.matchEntire(period) ?: return null
            val (y, mo, w, d) = m.destructured
            fun n(s: String) = s.toIntOrNull() ?: 0
            return n(y) * 365 + n(mo) * 30 + n(w) * 7 + n(d)
        }
    }
}

data class ProCatalog(val monthly: ProOffer, val annual: ProOffer) {
    /** `round(100 * (1 - annual / (12 * monthly)))`, at least 0. 0 when the monthly price is unknown. */
    val annualSavingsPercent: Int
        get() = if (monthly.priceMicros <= 0) {
            0
        } else {
            (100 * (1 - annual.priceMicros / (12.0 * monthly.priceMicros))).roundToInt().coerceAtLeast(0)
        }

    companion object {
        const val PRODUCT_ID = "pro"
    }
}
