/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.aiui

import com.manjul.aikeyboard.ime.policy.PolicyDecision
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoPolishPolicyTest {
    private val ok = PolicyDecision.Unrestricted

    private fun run(
        autoPolish: Boolean = true,
        consentAt: Long = 1L,
        isPro: Boolean = true,
        decision: PolicyDecision = ok,
        quotaPaused: Boolean = false,
    ) = AutoPolishPolicy.shouldAutoRun(autoPolish, consentAt, isPro, decision, quotaPaused)

    @Test fun allConditionsHold() = assertTrue(run())

    @Test fun toggleOff() = assertFalse(run(autoPolish = false))

    @Test fun noAutoPolishConsent() = assertFalse(run(consentAt = 0L))

    @Test fun notPro() = assertFalse(run(isPro = false))

    @Test fun aiNotAllowed() = assertFalse(run(decision = ok.copy(allowAi = false)))

    @Test fun cloudNotAllowed() = assertFalse(run(decision = ok.copy(allowCloud = false)))

    @Test fun sensitiveField() = assertFalse(run(decision = ok.copy(sensitive = true)))

    @Test fun quotaPaused() = assertFalse(run(quotaPaused = true))
}
