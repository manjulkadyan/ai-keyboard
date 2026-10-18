/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.policy

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `EditorInstance` is not instantiated here: it needs `FlorisApplication` (native library, managers). Its two
 * derivations and the service's restriction listener are one-line calls into [PolicyStateDerivation], so the
 * wiring is tested with the same calls on a real controller.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PolicyStateDerivationTest {
    @Test
    fun `unrestricted decision keeps the upstream values`() {
        assertTrue(PolicyStateDerivation.composingEnabled(true, PolicyDecision.Unrestricted))
        assertFalse(PolicyStateDerivation.composingEnabled(false, PolicyDecision.Unrestricted))
        assertFalse(PolicyStateDerivation.incognito(false, PolicyDecision.Unrestricted))
        assertTrue(PolicyStateDerivation.incognito(true, PolicyDecision.Unrestricted))
    }

    @Test
    fun `restricted decision turns composing off and incognito on`() {
        assertFalse(PolicyStateDerivation.composingEnabled(true, PolicyDecision.Locked))
        assertTrue(PolicyStateDerivation.incognito(false, PolicyDecision.Locked))
    }

    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `private package that loads after the field ends up incognito`() = runTest {
        val store = FeatureSettingsStore(RuntimeEnvironment.getApplication(), dispatcher)
        val controller = ImePolicyController(store, keyguard = { false }, forcedPackages = { emptySet() }, scope = scope)
        var incognito = false
        var composing = true
        // What FlorisImeService registers: re-apply the decision to the upstream values (false / true).
        controller.addRestrictionListener {
            incognito = PolicyStateDerivation.incognito(false, it)
            composing = PolicyStateDerivation.composingEnabled(true, it)
        }
        val info = EditorInfo().apply {
            packageName = "com.bank"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        controller.onStartInput(info, false) // default settings
        incognito = PolicyStateDerivation.incognito(false, controller.decision.value)
        composing = PolicyStateDerivation.composingEnabled(true, controller.decision.value)
        assertFalse(incognito)
        assertTrue(composing)

        store.update { it.copy(privatePackages = setOf("com.bank")) }

        assertTrue(incognito)
        assertFalse(composing)
        assertEquals(false, controller.decision.value.allowLearning)
    }
}
