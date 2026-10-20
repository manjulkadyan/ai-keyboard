/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.smartbar

import com.manjul.aikeyboard.ime.policy.PolicyDecision
import com.manjul.aikeyboard.ime.policy.SensitivityReason
import com.manjul.aikeyboard.ime.smartbar.quickaction.QuickAction
import com.manjul.aikeyboard.ime.text.key.KeyCode
import com.manjul.aikeyboard.ime.text.keyboard.TextKeyData
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PolicyQuickActionsTest {
    private fun key(d: com.manjul.aikeyboard.ime.keyboard.KeyData) = QuickAction.InsertKey(d)
    private val mic = key(TextKeyData.VOICE_INPUT)
    private val emoji = key(TextKeyData.IME_UI_MODE_MEDIA)
    private val clipboard = key(TextKeyData.IME_UI_MODE_CLIPBOARD)
    private val settings = key(TextKeyData.SETTINGS)
    private val makePrivate = key(TextKeyData.TOGGLE_PRIVATE_APP)
    private val undo = key(TextKeyData.UNDO)
    private val privateApp = PolicyDecision.Locked.copy(reasons = setOf(SensitivityReason.PRIVATE_APP))

    @Test
    fun `no controller falls back to Locked`() {
        assertEquals(PolicyDecision.Locked, decisionOrLocked(null))
        assertEquals(PolicyDecision.Unrestricted, decisionOrLocked(PolicyDecision.Unrestricted))
    }

    @Test
    fun `locked removes mic and clipboard but keeps emoji and settings`() {
        val d = decisionOrLocked(null)
        assertFalse(mic.isAllowedBy(d, true))
        assertFalse(clipboard.isAllowedBy(d, true))
        assertTrue(emoji.isAllowedBy(d, true))
        assertTrue(settings.isAllowedBy(d, true))
    }

    @Test
    fun `private app decision removes mic and clipboard`() {
        assertFalse(mic.isAllowedBy(privateApp, true))
        assertFalse(clipboard.isAllowedBy(privateApp, true))
        assertTrue(emoji.isAllowedBy(privateApp, true))
    }

    @Test
    fun `unrestricted allows chips and clipboard follows the history pref`() {
        val d = PolicyDecision.Unrestricted
        assertTrue(mic.isAllowedBy(d, false))
        assertTrue(clipboard.isAllowedBy(d, true))
        assertFalse(clipboard.isAllowedBy(d, false))
    }

    @Test
    fun `make private action is hidden only when the app is already private`() {
        assertTrue(makePrivate.isAllowedBy(PolicyDecision.Unrestricted, true))
        assertTrue(makePrivate.isAllowedBy(PolicyDecision.Locked, true))
        assertFalse(makePrivate.isAllowedBy(privateApp, true))
    }

    @Test
    fun `sensitive fields show only emoji, settings and the private-app action`() {
        val password = PolicyDecision.Locked.copy(reasons = setOf(SensitivityReason.PASSWORD))
        for (d in listOf(PolicyDecision.Locked, password)) {
            assertTrue(emoji.isAllowedBy(d, true))
            assertTrue(settings.isAllowedBy(d, true))
            assertTrue(makePrivate.isAllowedBy(d, true))
            assertFalse(undo.isAllowedBy(d, true))
            assertFalse(key(TextKeyData.REDO).isAllowedBy(d, true))
            assertFalse(key(TextKeyData.CLIPBOARD_PASTE).isAllowedBy(d, true))
            assertFalse(QuickAction.InsertText("x").isAllowedBy(d, true))
        }
    }

    @Test
    fun `non chip actions stay in normal fields`() {
        assertTrue(undo.isAllowedBy(PolicyDecision.Unrestricted, false))
        assertTrue(QuickAction.InsertText("x").isAllowedBy(PolicyDecision.Unrestricted, false))
    }

    @Test
    fun `forKeyCode maps chip keys and ignores others`() {
        assertEquals(ChipId.MIC, ChipId.forKeyCode(KeyCode.VOICE_INPUT))
        assertEquals(ChipId.EMOJI, ChipId.forKeyCode(KeyCode.IME_UI_MODE_MEDIA))
        assertEquals(ChipId.CLIPBOARD, ChipId.forKeyCode(KeyCode.IME_UI_MODE_CLIPBOARD))
        assertEquals(ChipId.SETTINGS, ChipId.forKeyCode(KeyCode.SETTINGS))
        assertNull(ChipId.forKeyCode(KeyCode.UNDO))
        assertNull(ChipId.forKeyCode(KeyCode.TOGGLE_PRIVATE_APP))
    }
}
