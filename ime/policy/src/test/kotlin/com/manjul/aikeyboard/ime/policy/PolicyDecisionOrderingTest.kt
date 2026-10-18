/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.policy

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PolicyDecisionOrderingTest {
    @Test
    fun `unrestricted is not more restrictive than locked`() {
        assertFalse(PolicyDecision.Unrestricted.isMoreRestrictiveThan(PolicyDecision.Locked))
    }

    @Test
    fun `locked is more restrictive than unrestricted`() {
        assertTrue(PolicyDecision.Locked.isMoreRestrictiveThan(PolicyDecision.Unrestricted))
    }

    @Test
    fun `equal decisions are not more restrictive`() {
        assertFalse(PolicyDecision.Locked.isMoreRestrictiveThan(PolicyDecision.Locked.copy()))
        assertFalse(PolicyDecision.Unrestricted.isMoreRestrictiveThan(PolicyDecision.Unrestricted.copy()))
    }

    @Test
    fun `a single flag flipping to false counts`() {
        val narrower = PolicyDecision.Unrestricted.copy(allowVoice = false)
        assertTrue(narrower.isMoreRestrictiveThan(PolicyDecision.Unrestricted))
        assertFalse(PolicyDecision.Unrestricted.isMoreRestrictiveThan(narrower))
    }
}
