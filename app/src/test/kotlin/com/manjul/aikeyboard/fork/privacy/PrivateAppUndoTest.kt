/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.privacy

import android.app.Application
import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.fork.policy.ImePolicyController
import com.manjul.aikeyboard.fork.smartbar.isAllowedBy
import com.manjul.aikeyboard.fork.smartbar.showsPrivateLock
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import com.manjul.aikeyboard.ime.policy.SensitivityReason
import com.manjul.aikeyboard.ime.smartbar.quickaction.QuickAction
import com.manjul.aikeyboard.ime.text.keyboard.TextKeyData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
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
 * Long-press "Make this app private" adds the app at once and shows "App made private" with Undo for 5 s.
 * The policy controller, store and repository are the real ones; only time is virtual.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PrivateAppUndoTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val app = RuntimeEnvironment.getApplication()
    private val store = FeatureSettingsStore(app, dispatcher)
    private val repo = PrivateAppsRepository(store, app.packageManager)
    private val controller = ImePolicyController(store, { false }, { emptySet() }, scope)
    private val undo = PrivateAppUndo(add = repo::add, remove = repo::remove, scope = scope)

    private val mic = QuickAction.InsertKey(TextKeyData.VOICE_INPUT)
    private val makePrivate = QuickAction.InsertKey(TextKeyData.TOGGLE_PRIVATE_APP)

    @After
    fun tearDown() = scope.cancel()

    private fun field(pkg: String) = EditorInfo().apply {
        packageName = pkg
        inputType = InputType.TYPE_CLASS_TEXT
    }

    private val decision: PolicyDecision get() = controller.decision.value
    private val isPrivate: Boolean get() = PKG in store.current.privatePackages

    private fun TestScope.longPressMakePrivate() {
        controller.onStartInput(field(PKG), false)
        assertTrue(makePrivate.isAllowedBy(decision, clipboardHistoryOn = true))
        assertTrue(mic.isAllowedBy(decision, clipboardHistoryOn = true))
        undo.makePrivate(PKG)
        runCurrent()
    }

    @Test
    fun `long-press makes the app private, shows the message and turns the lock on`() = runTest(dispatcher) {
        longPressMakePrivate()

        assertTrue(isPrivate)
        assertTrue(undo.visible.value)
        assertTrue(SensitivityReason.PRIVATE_APP in decision.reasons)
        assertTrue(decision.showsPrivateLock())
        assertFalse(makePrivate.isAllowedBy(decision, clipboardHistoryOn = true))
        assertFalse(mic.isAllowedBy(decision, clipboardHistoryOn = true))
    }

    @Test
    fun `undo within 5 s removes the app and restores the toggle and the mic live`() = runTest(dispatcher) {
        longPressMakePrivate()
        advanceTimeBy(4_000)
        assertTrue(undo.visible.value)

        undo.undo()
        runCurrent()

        assertFalse(isPrivate)
        assertFalse(undo.visible.value)
        assertFalse(SensitivityReason.PRIVATE_APP in decision.reasons)
        assertFalse(decision.showsPrivateLock())
        assertTrue(makePrivate.isAllowedBy(decision, clipboardHistoryOn = true))
        assertTrue(mic.isAllowedBy(decision, clipboardHistoryOn = true))
    }

    @Test
    fun `after 5 s the message is gone and the app stays private`() = runTest(dispatcher) {
        longPressMakePrivate()

        advanceTimeBy(PrivateAppUndo.WINDOW_MS + 1)
        runCurrent()

        assertFalse(undo.visible.value)
        assertTrue(isPrivate)
        undo.undo() // too late: nothing to undo
        runCurrent()
        assertTrue(isPrivate)
        assertTrue(decision.showsPrivateLock())
    }

    @Test
    fun `a field change dismisses the message and the app stays private`() = runTest(dispatcher) {
        longPressMakePrivate()

        undo.onFieldChange()
        controller.onStartInput(field("com.other"), false)
        runCurrent()

        assertFalse(undo.visible.value)
        assertTrue(isPrivate)
        undo.undo()
        runCurrent()
        assertTrue(isPrivate)
    }

    @Test
    fun `a field change while the add is in flight shows no message`() = runTest(dispatcher) {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val slow = PrivateAppUndo(add = { gate.await(); repo.add(it) }, remove = repo::remove, scope = scope)

        slow.makePrivate(PKG)
        slow.onFieldChange()
        gate.complete(Unit)
        runCurrent()

        assertTrue(isPrivate)
        assertFalse(slow.visible.value)
    }

    @Test
    fun `a blank package does nothing`() = runTest(dispatcher) {
        undo.makePrivate(null)
        undo.makePrivate(" ")
        runCurrent()
        assertFalse(undo.visible.value)
        assertEquals(emptySet(), store.current.privatePackages)
    }

    private companion object {
        const val PKG = "com.example.notes"
    }
}
