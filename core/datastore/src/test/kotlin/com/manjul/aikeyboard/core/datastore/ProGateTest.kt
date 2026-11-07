/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.datastore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The cached kb-api verdict is valid until `expires_at` plus 3 days of grace (plan decision 2). */
class ProGateTest {
    private val expires = 1_800_000_000_000L
    private val grace = FeatureSettings.PRO_CACHE_GRACE_MS
    private val pro = FeatureSettings(proActive = true, proState = "active", proExpiresAt = expires)

    @Test fun `grace is three days`() {
        assertEquals(3L * 24 * 60 * 60 * 1000, grace)
    }

    @Test fun `pro before expiry`() {
        assertTrue(pro.isProAt(expires - 1))
        assertTrue(pro.isProAt(0L))
    }

    @Test fun `pro through the grace window`() {
        assertTrue(pro.isProAt(expires))
        assertTrue(pro.isProAt(expires + grace - 1))
    }

    @Test fun `free once the grace window ends`() {
        assertFalse(pro.isProAt(expires + grace))
        assertFalse(pro.isProAt(expires + grace + 1))
    }

    @Test fun `free when the server said not active`() {
        assertFalse(pro.copy(proActive = false).isProAt(0L))
    }

    @Test fun `defaults are free`() {
        val s = FeatureSettings()
        assertFalse(s.proActive)
        assertEquals("none", s.proState)
        assertEquals(0L, s.proExpiresAt)
        assertEquals(0L, s.proIssuedAt)
        assertEquals("", s.proSignature)
        assertEquals(0L, s.proCheckedAt)
        assertFalse(s.isProAt(0L))
    }
}
