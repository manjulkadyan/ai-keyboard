/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.billing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProCatalogTest {
    @Test
    fun `annual 19_99 against monthly 2_99 saves 44 percent`() {
        val catalog = ProCatalog(
            monthly = FakeBillingGateway.offer(Plan.MONTHLY, micros = 2_990_000L),
            annual = FakeBillingGateway.offer(Plan.ANNUAL, micros = 19_990_000L),
        )
        assertEquals(44, catalog.annualSavingsPercent)
    }

    @Test
    fun `a zero monthly price gives no savings instead of a division by zero`() {
        val catalog = ProCatalog(
            monthly = FakeBillingGateway.offer(Plan.MONTHLY, micros = 0L),
            annual = FakeBillingGateway.offer(Plan.ANNUAL, micros = 19_990_000L),
        )
        assertEquals(0, catalog.annualSavingsPercent)
    }

    @Test
    fun `trial days come from the free pricing phase`() {
        val free = PricePhase(priceMicros = 0L, formattedPrice = "Free", currency = "USD", billingPeriod = "P7D")
        val paid = PricePhase(19_990_000L, "$19.99", "USD", "P1Y")
        val offer = ProOffer.from(Plan.ANNUAL, "annual", "tok-offer", listOf(free, paid))!!
        assertEquals(7, offer.trialDays)
        assertEquals(19_990_000L, offer.priceMicros)
        assertEquals("$19.99", offer.formattedPrice)
        assertEquals("P1Y", offer.billingPeriod)
        assertEquals("USD", offer.currency)
    }

    @Test
    fun `a one week free phase is 7 days and a paid-only offer has no trial`() {
        val week = PricePhase(0L, "Free", "USD", "P1W")
        val paid = PricePhase(2_990_000L, "$2.99", "USD", "P1M")
        assertEquals(7, ProOffer.from(Plan.ANNUAL, "annual", "t", listOf(week, paid))!!.trialDays)
        assertNull(ProOffer.from(Plan.MONTHLY, "monthly", "t", listOf(paid))!!.trialDays)
        assertNull(ProOffer.from(Plan.MONTHLY, "monthly", "t", emptyList()))
    }

    @Test
    fun `plan wire names`() {
        assertEquals(listOf("monthly", "annual"), Plan.entries.map { it.wire })
    }
}
