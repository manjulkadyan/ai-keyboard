/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ads.consent

import kotlin.test.Test
import kotlin.test.assertEquals

class ConsentModeSignalsTest {
    private val allPurposes = "1111111111"
    private val googleConsent = "0".repeat(754) + "1"

    @Test
    fun `no ad requests denies every signal`() {
        assertEquals(ConsentModeSignals.DENIED, ConsentModeSignals.from(false, gdprApplies = 0, null, null))
    }

    @Test
    fun `outside GDPR every signal is granted`() {
        assertEquals(ConsentModeSignals.GRANTED, ConsentModeSignals.from(true, gdprApplies = 0, null, null))
        assertEquals(ConsentModeSignals.GRANTED, ConsentModeSignals.from(true, gdprApplies = null, null, null))
    }

    @Test
    fun `under GDPR full consent for Google grants every signal`() {
        assertEquals(ConsentModeSignals.GRANTED, ConsentModeSignals.from(true, 1, allPurposes, googleConsent))
    }

    @Test
    fun `under GDPR without Google vendor consent every signal is denied`() {
        assertEquals(ConsentModeSignals.DENIED, ConsentModeSignals.from(true, 1, allPurposes, "1".repeat(754) + "0"))
        assertEquals(ConsentModeSignals.DENIED, ConsentModeSignals.from(true, 1, allPurposes, null))
    }

    @Test
    fun `under GDPR purposes map to the Consent Mode v2 signals`() {
        // Purpose 1 only: storage, no personalisation (3, 4) and no ad user data (7).
        assertEquals(
            ConsentModeSignals(adStorage = true, adUserData = false, adPersonalization = false, analyticsStorage = true),
            ConsentModeSignals.from(true, 1, "1000000000", googleConsent),
        )
        // Purposes 1, 3, 4 and 7.
        assertEquals(
            ConsentModeSignals.GRANTED,
            ConsentModeSignals.from(true, 1, "1011001000", googleConsent),
        )
        // Everything but purpose 1.
        assertEquals(
            ConsentModeSignals(adStorage = false, adUserData = false, adPersonalization = true, analyticsStorage = false),
            ConsentModeSignals.from(true, 1, "0111111111", googleConsent),
        )
    }

    @Test
    fun `under GDPR a missing purpose string denies every signal`() {
        assertEquals(ConsentModeSignals.DENIED, ConsentModeSignals.from(true, 1, null, googleConsent))
    }
}
