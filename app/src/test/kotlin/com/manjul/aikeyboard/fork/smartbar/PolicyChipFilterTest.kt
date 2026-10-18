/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.smartbar

import com.manjul.aikeyboard.fork.smartbar.ChipId.*
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import com.manjul.aikeyboard.ime.policy.SensitivityReason
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PolicyChipFilterTest {
    private val all = ChipId.entries.toList()
    private val allRegistered = ChipId.entries.toSet()

    // Same capabilities as SensitiveFieldPolicy gives a password field.
    private val password = PolicyDecision.Locked.copy(reasons = setOf(SensitivityReason.PASSWORD))

    @Test
    fun `password field keeps only emoji and settings`() {
        val visible = ChipArrangement.visible(all, allRegistered, password, clipboardHistoryOn = true)
        assertEquals(listOf(EMOJI, SETTINGS), visible)
    }

    @Test
    fun `unrestricted field shows every registered chip in order`() {
        val visible = ChipArrangement.visible(all, allRegistered, PolicyDecision.Unrestricted, true)
        assertEquals(all, visible)
    }

    @Test
    fun `unregistered translate and ai are never visible`() {
        val registered = setOf(MIC, EMOJI, CLIPBOARD, SETTINGS)
        val visible = ChipArrangement.visible(all, registered, PolicyDecision.Unrestricted, true)
        assertFalse(TRANSLATE in visible)
        assertFalse(AI in visible)
        assertEquals(listOf(MIC, EMOJI, CLIPBOARD, SETTINGS), visible)
    }

    @Test
    fun `clipboard is hidden when history is off`() {
        val visible = ChipArrangement.visible(all, allRegistered, PolicyDecision.Unrestricted, false)
        assertFalse(CLIPBOARD in visible)
        assertTrue(EMOJI in visible)
    }

    @Test
    fun `locked decision hides everything but emoji and settings`() {
        val visible = ChipArrangement.visible(all, allRegistered, PolicyDecision.Locked, true)
        assertEquals(listOf(EMOJI, SETTINGS), visible)
    }
}
