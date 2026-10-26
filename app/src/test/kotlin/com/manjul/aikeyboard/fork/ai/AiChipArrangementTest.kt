/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.ai

import android.text.InputType
import com.manjul.aikeyboard.fork.smartbar.ChipArrangement
import com.manjul.aikeyboard.fork.smartbar.ChipId
import com.manjul.aikeyboard.fork.smartbar.PinnedActions
import com.manjul.aikeyboard.fork.smartbar.isAllowedBy
import com.manjul.aikeyboard.ime.policy.FieldDescriptor
import com.manjul.aikeyboard.ime.policy.InputTypeBits
import com.manjul.aikeyboard.ime.policy.PolicyContext
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import com.manjul.aikeyboard.ime.policy.SensitiveFieldPolicy
import com.manjul.aikeyboard.ime.smartbar.quickaction.QuickAction
import com.manjul.aikeyboard.ime.smartbar.quickaction.QuickActionArrangement
import com.manjul.aikeyboard.ime.text.key.KeyCode
import com.manjul.aikeyboard.ime.text.keyboard.TextKeyData
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiChipArrangementTest {
    private val all = ChipId.entries.toList()
    private val ai = QuickAction.InsertKey(TextKeyData.AI_REWRITE)

    private fun decide(inputType: Int = InputType.TYPE_CLASS_TEXT, imeOptions: Int = 0, incognito: Boolean = false) =
        SensitiveFieldPolicy.evaluate(
            FieldDescriptor(packageName = "some.app", inputType = inputType, imeOptions = imeOptions, hintText = null, label = null),
            PolicyContext(
                deviceLocked = false,
                incognitoManual = incognito,
                privatePackages = emptySet(),
                forceSensitivePackages = emptySet(),
            ),
        )

    @Test
    fun `the AI chip is wired to its own key code and registered`() {
        assertEquals(KeyCode.AI_REWRITE, ChipId.AI.keyCode)
        assertEquals(ChipId.AI, ChipId.forKeyCode(KeyCode.AI_REWRITE))
        assertTrue(ChipId.AI in ChipId.registered)
        assertEquals(KeyCode.AI_REWRITE, TextKeyData.AI_REWRITE.code)
    }

    @Test
    fun `the AI chip shows in a text field where AI and cloud are allowed`() {
        val text = decide()
        assertTrue(text.allowAi && text.allowCloud)
        assertTrue(ChipId.AI in ChipArrangement.visible(all, ChipId.registered, text, true))
        assertTrue(ai.isAllowedBy(text, true, aiAvailable = true))
    }

    @Test
    fun `the AI chip is absent for password, incognito and no-personalized-learning fields`() {
        val decisions = mapOf(
            "password" to decide(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD),
            "incognito" to decide(incognito = true),
            "no personalized learning" to decide(imeOptions = InputTypeBits.IME_FLAG_NO_PERSONALIZED_LEARNING),
            "email" to decide(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS),
            "locked" to PolicyDecision.Locked,
        )
        for ((name, d) in decisions) {
            assertFalse(ChipId.AI in ChipArrangement.visible(all, ChipId.registered, d, true), name)
            // Even when the session says AI is available, the decision wins.
            assertFalse(ai.isAllowedBy(d, true, aiAvailable = true), name)
        }
    }

    @Test
    fun `AI needs the cloud too`() {
        val noCloud = PolicyDecision.Unrestricted.copy(allowCloud = false)
        assertFalse(ChipArrangement.allowed(ChipId.AI, noCloud, true))
        assertFalse(ai.isAllowedBy(noCloud, true, aiAvailable = true))
    }

    @Test
    fun `the AI chip is hidden while the session says AI is unavailable`() {
        assertFalse(ai.isAllowedBy(decide(), true, aiAvailable = false))
        assertFalse(ai.isAllowedBy(decide(), true))
    }

    @Test
    fun `the default arrangement pins AI and respects MAX_PINS`() {
        val default = QuickActionArrangement.Default
        assertTrue(ai in default.dynamicActions)
        assertTrue(PinnedActions.pinnedCount(default.dynamicActions) <= ChipArrangement.MAX_PINS)
        assertEquals(default, PinnedActions.normalize(default))
        // A 4th pin is refused.
        assertFalse(PinnedActions.canPin(default.dynamicActions, QuickAction.InsertKey(TextKeyData.UNDO)))
        // Settings stays reachable from the overflow.
        assertTrue(QuickAction.InsertKey(TextKeyData.SETTINGS) in default)
    }
}
