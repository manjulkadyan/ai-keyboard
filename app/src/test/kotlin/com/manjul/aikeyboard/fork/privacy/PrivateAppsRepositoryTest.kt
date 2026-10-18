/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.privacy

import android.app.Application
import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.fork.policy.ImePolicyController
import com.manjul.aikeyboard.ime.policy.SensitivityReason
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PrivateAppsRepositoryTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val app = RuntimeEnvironment.getApplication()
    private val store = FeatureSettingsStore(app, dispatcher)
    private val logged = mutableListOf<AnalyticsEvent>()
    private val repo = PrivateAppsRepository(store, app.packageManager) { logged += it }
    private val controller = ImePolicyController(store, { false }, { emptySet() }, scope)

    @After
    fun tearDown() = scope.cancel()

    private fun field(pkg: String) = EditorInfo().apply {
        packageName = pkg
        inputType = InputType.TYPE_CLASS_TEXT
    }

    @Test
    fun `added package is reported as private on the next start input`() = runTest {
        controller.onStartInput(field("com.bank"), false)
        assertFalse(SensitivityReason.PRIVATE_APP in controller.decision.value.reasons)

        repo.add("com.bank")
        controller.onStartInput(field("com.bank"), false)

        assertTrue(SensitivityReason.PRIVATE_APP in controller.decision.value.reasons)
        assertTrue(controller.decision.value.sensitive)
    }

    @Test
    fun `removed package is no longer private`() = runTest {
        repo.add("com.bank")
        repo.remove("com.bank")
        controller.onStartInput(field("com.bank"), false)
        assertFalse(SensitivityReason.PRIVATE_APP in controller.decision.value.reasons)
    }

    @Test
    fun `add logs the event without params`() = runTest {
        repo.add("com.bank")
        assertEquals(listOf(AnalyticsEvent("privacy_private_app_add", emptyMap())), logged)
    }

    @Test
    fun `remove does not log`() = runTest {
        repo.remove("com.bank")
        assertTrue(logged.isEmpty())
    }

    @Test
    fun `suggestions are empty while the banking list is empty`() {
        assertTrue(repo.suggestions().isEmpty())
    }
}
