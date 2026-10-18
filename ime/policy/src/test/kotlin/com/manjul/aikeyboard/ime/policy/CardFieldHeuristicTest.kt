/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.policy

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardFieldHeuristicTest {
    private val plain = PolicyContext(false, false, emptySet(), emptySet())
    private val numberType = 0x02
    private val textType = 0x01

    private fun number(hint: String? = null, label: String? = null) =
        FieldDescriptor("com.example", numberType, 0, hint, label)

    @ParameterizedTest
    @ValueSource(strings = ["card", "Card Number", "CVV", "cvc", "IBAN", "PIN", "Enter OTP", "one-time code", "one time code", "onetime"])
    fun `number field with matching hint is soft restricted`(text: String) {
        val d = SensitiveFieldPolicy.evaluate(number(hint = text), plain)
        assertEquals(setOf(SensitivityReason.CARD_HEURISTIC), d.reasons)
        assertFalse(d.sensitive)
        assertFalse(d.allowLearning)
        assertFalse(d.allowTranslate)
        assertFalse(d.allowAi)
        assertFalse(d.allowCloud)
        assertFalse(d.allowVoice)
        assertFalse(d.allowClipboardHistory)
        assertTrue(d.allowSuggestionHistory)
        assertTrue(d.allowSuggestionStrip)
        assertFalse(d.analyticsMinimal)
    }

    @Test
    fun `label is checked as well as hint`() {
        val d = SensitiveFieldPolicy.evaluate(number(label = "Security code (CVV)"), plain)
        assertTrue(SensitivityReason.CARD_HEURISTIC in d.reasons)
    }

    @Test
    fun `text class with card hint has no reason`() {
        val d = SensitiveFieldPolicy.evaluate(FieldDescriptor("com.example", textType, 0, "card", "card"), plain)
        assertEquals(PolicyDecision.Unrestricted, d)
    }

    @Test
    fun `word boundary prevents partial matches`() {
        for (text in listOf("spinner", "discard", "pinned", "hotel", "carded")) {
            val d = SensitiveFieldPolicy.evaluate(number(hint = text), plain)
            assertTrue(d.reasons.isEmpty(), text)
        }
    }

    @Test
    fun `null hint and label do not match`() {
        assertTrue(SensitiveFieldPolicy.evaluate(number(), plain).reasons.isEmpty())
    }

    @Test
    fun `card reason is collected alongside a sensitive reason`() {
        val d = SensitiveFieldPolicy.evaluate(
            FieldDescriptor("com.example", 0x12, 0, "PIN", null),
            plain,
        )
        assertEquals(setOf(SensitivityReason.NUMBER_PASSWORD, SensitivityReason.CARD_HEURISTIC), d.reasons)
        assertTrue(d.sensitive)
    }
}
