/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.policy

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import com.manjul.aikeyboard.ime.policy.SensitivityReason
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ImePolicyControllerTest {
    private var locked = false
    private val store = FeatureSettingsStore(RuntimeEnvironment.getApplication())
    private val controller = ImePolicyController(store, keyguard = { locked }, forcedPackages = { emptySet() })

    private fun text(pkg: String = "some.app") = EditorInfo().apply {
        packageName = pkg
        inputType = InputType.TYPE_CLASS_TEXT
    }

    private fun password() = EditorInfo().apply {
        packageName = "some.app"
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    @Test
    fun `text field allows voice`() {
        controller.onStartInput(text(), false)
        assertTrue(controller.decision.value.allowVoice)
    }

    @Test
    fun `restart into a password field notifies once and bumps generation`() {
        controller.onStartInput(text(), false)
        val seen = mutableListOf<PolicyDecision>()
        controller.addRestrictionListener { seen += it }
        val before = controller.generation.value

        controller.onStartInput(password(), restarting = true)

        assertEquals(1, seen.size)
        assertFalse(seen.single().allowVoice)
        assertNotEquals(before, controller.generation.value)
    }

    @Test
    fun `restart into a less restrictive field does not notify`() {
        controller.onStartInput(password(), false)
        var calls = 0
        controller.addRestrictionListener { calls++ }
        val before = controller.generation.value

        controller.onStartInput(text(), restarting = true)

        assertEquals(0, calls)
        assertEquals(before, controller.generation.value)
        assertTrue(controller.decision.value.allowVoice)
    }

    @Test
    fun `null EditorInfo does not crash and is unrestricted`() {
        controller.onStartInput(null, false)
        assertEquals(PolicyDecision.Unrestricted, controller.decision.value)
    }

    @Test
    fun `locked device is evaluated on each start`() {
        locked = true
        controller.onStartInput(text(), false)
        assertTrue(SensitivityReason.DEVICE_LOCKED in controller.decision.value.reasons)
        locked = false
        controller.onStartInput(text(), false)
        assertFalse(SensitivityReason.DEVICE_LOCKED in controller.decision.value.reasons)
    }

    @Test
    fun `private package is restricted`() = runTest {
        store.update { it.copy(privatePackages = setOf("secret.app")) }
        controller.onStartInput(text("secret.app"), false)
        assertTrue(SensitivityReason.PRIVATE_APP in controller.decision.value.reasons)
    }

    @Test
    fun `finishing the input view bumps generation`() {
        val before = controller.generation.value
        controller.onFinishInputView()
        assertNotEquals(before, controller.generation.value)
        assertFalse(controller.generation.isCurrent(before))
    }
}
