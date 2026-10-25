/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.smartbar

import com.manjul.aikeyboard.fork.smartbar.ChipId.*
import android.text.InputType
import com.manjul.aikeyboard.ime.policy.FieldDescriptor
import com.manjul.aikeyboard.ime.policy.PolicyContext
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import com.manjul.aikeyboard.ime.policy.SensitiveFieldPolicy
import com.manjul.aikeyboard.ime.smartbar.quickaction.QuickAction
import com.manjul.aikeyboard.ime.text.keyboard.TextKeyData
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PolicyChipFilterTest {
    private val all = ChipId.entries.toList()
    private val allRegistered = ChipId.entries.toSet()

    private val password = SensitiveFieldPolicy.evaluate(
        FieldDescriptor(
            packageName = "some.app",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            imeOptions = 0,
            hintText = null,
            label = null,
        ),
        PolicyContext(
            deviceLocked = false,
            incognitoManual = false,
            privatePackages = emptySet(),
            forceSensitivePackages = emptySet(),
        ),
    )

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

    private fun decide(inputType: Int, privateApp: Boolean = false) = SensitiveFieldPolicy.evaluate(
        FieldDescriptor(packageName = "some.app", inputType = inputType, imeOptions = 0, hintText = null, label = null),
        PolicyContext(
            deviceLocked = false,
            incognitoManual = false,
            privatePackages = if (privateApp) setOf("some.app") else emptySet(),
            forceSensitivePackages = emptySet(),
        ),
    )

    private val translateAction = QuickAction.InsertKey(TextKeyData.TOGGLE_TRANSLATE_BAR)

    @Test
    fun `translate is registered and visible in a text field`() {
        val text = decide(InputType.TYPE_CLASS_TEXT)
        assertTrue(TRANSLATE in ChipId.registered)
        assertTrue(TRANSLATE in ChipArrangement.visible(all, ChipId.registered, text, true))
        assertTrue(translateAction.isAllowedBy(text, true))
    }

    @Test
    fun `translate is hidden for password, phone, email, uri and private-app fields`() {
        val decisions = mapOf(
            "password" to password,
            "phone" to decide(InputType.TYPE_CLASS_PHONE),
            "email" to decide(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS),
            "uri" to decide(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI),
            "private app" to decide(InputType.TYPE_CLASS_TEXT, privateApp = true),
        )
        for ((name, d) in decisions) {
            assertFalse(d.allowTranslate, name)
            assertFalse(TRANSLATE in ChipArrangement.visible(all, ChipId.registered, d, true), name)
            assertFalse(translateAction.isAllowedBy(d, true), name)
        }
    }

    @Test
    fun `registering translate leaves the other chips' visibility unchanged`() {
        val decisions = listOf(
            PolicyDecision.Unrestricted, PolicyDecision.Locked, password,
            decide(InputType.TYPE_CLASS_PHONE),
            decide(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS),
            decide(InputType.TYPE_CLASS_TEXT, privateApp = true),
        )
        val withoutTranslate = ChipId.registered - TRANSLATE
        for (d in decisions) for (clipboard in listOf(true, false)) {
            assertEquals(
                ChipArrangement.visible(all, withoutTranslate, d, clipboard),
                ChipArrangement.visible(all, ChipId.registered, d, clipboard) - TRANSLATE,
            )
        }
    }

    @Test
    fun `the private lock shows only where translation, voice and learning are off`() {
        val decisions = listOf(
            PolicyDecision.Locked, password,
            decide(InputType.TYPE_CLASS_TEXT, privateApp = true),
            decide(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD),
            decide(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS),
            decide(InputType.TYPE_CLASS_PHONE),
            PolicyDecision.Unrestricted,
        )
        for (d in decisions.filter { it.showsPrivateLock() }) {
            assertFalse(d.allowTranslate || d.allowVoice || d.allowLearning, d.reasons.toString())
        }
        assertTrue(decisions.count { it.showsPrivateLock() } >= 4)
    }
}
