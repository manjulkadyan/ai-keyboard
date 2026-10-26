/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.ai

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText
import com.manjul.aikeyboard.core.ai.AiEvent
import com.manjul.aikeyboard.core.ai.AiLru
import com.manjul.aikeyboard.core.ai.AiPauseGate
import com.manjul.aikeyboard.core.ai.FakeAiEngine
import com.manjul.aikeyboard.core.ai.FakeTokenSource
import com.manjul.aikeyboard.core.ai.RewriteMode
import com.manjul.aikeyboard.core.analytics.ConsentKind
import com.manjul.aikeyboard.core.config.DefaultsRemoteConfig
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.fork.policy.ImePolicyController
import com.manjul.aikeyboard.ime.aiui.AiSheetState
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The ✦ rewrite in the keyboard, end to end. FlorisImeService cannot run under Robolectric (native library), so this
 * drives [AiImeSession] the way the service does: the real policy controller gets the lifecycle calls and a real
 * EditText input connection is the host. The engine is a fake; nothing leaves the test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AiImeSessionTest {
    private val app = RuntimeEnvironment.getApplication()

    inner class Rig(private val scope: TestScope, initial: String = "hello world", configured: Boolean = true) {
        val edit = EditText(app).apply {
            setText(initial)
            setSelection(0, initial.length)
        }
        val commits = mutableListOf<String>()
        val ic: InputConnection = object : InputConnectionWrapper(edit.onCreateInputConnection(EditorInfo()), false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                commits += text.toString()
                return super.commitText(text, newCursorPosition)
            }
        }
        val engine = FakeAiEngine()
        val tokens = FakeTokenSource()
        val consents = mutableListOf<ConsentKind>()
        val store = FeatureSettingsStore(app, StandardTestDispatcher(scope.testScheduler))
        val policy = ImePolicyController(store, keyguard = { false }, forcedPackages = { emptySet() }, scope = scope.backgroundScope)
        val graph = AiAppGraph(
            config = DefaultsRemoteConfig(),
            tokens = tokens,
            engine = engine,
            lru = AiLru(),
            pause = AiPauseGate { scope.testScheduler.currentTime },
            report = {},
            isConfigured = configured,
        )
        val session = AiImeSession(
            connection = { ic },
            decision = { policy.decision.value },
            generation = policy.generation,
            settings = store,
            graph = graph,
            launchConsent = { consents += it },
            isPro = { false },
            scope = scope.backgroundScope,
            clock = { scope.testScheduler.currentTime },
        ).also { it.bindTo(policy) }
        val text get() = edit.text.toString()
        val state get() = session.rewrite.state.value

        /** As FlorisImeService: session first (a new connection holds nothing of ours), then the policy. */
        fun startField(info: EditorInfo, restarting: Boolean = false) {
            session.onStartInput(restarting)
            policy.onStartInput(info, restarting)
            if (!restarting) session.onStartInputView()
        }

        fun hide() {
            policy.onFinishInputView()
            session.onFinishInputView()
        }
    }

    private fun textField() = EditorInfo().apply {
        packageName = "some.app"
        inputType = InputType.TYPE_CLASS_TEXT
    }

    private fun passwordField() = EditorInfo().apply {
        packageName = "some.app"
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    private fun run(block: suspend TestScope.() -> Unit) = runTest(StandardTestDispatcher()) { block() }

    private suspend fun TestScope.consent(r: Rig) {
        r.store.update { it.copy(consentAiAt = 1L) }
        runCurrent()
    }

    private fun done() = AiEvent.Done(quotaLeft = 9, trimmed = false, polished = null, translation = null, sourceLangDetected = null)

    @Test fun fullFlowToReplaceEditsTheFieldAndUndoRestoresIt() = run {
        val r = Rig(this)
        consent(r)
        r.startField(textField())
        runCurrent()
        assertTrue(r.session.chipAvailable.value)
        r.engine.respondWith(AiEvent.Delta("Hello, "), AiEvent.Delta("world."), done())

        r.session.open()
        assertIs<AiSheetState.ChooseTone>(r.state)
        r.session.rewrite.chooseTone(RewriteMode.FIX)
        runCurrent()
        assertEquals("Hello, world.", assertIs<AiSheetState.Ready>(r.state).text)
        assertEquals("hello world", r.text, "the preview stays in the sheet")

        r.session.rewrite.replace()
        assertEquals("Hello, world.", r.text)
        assertIs<AiSheetState.Replaced>(r.state)
        assertTrue(r.session.rewrite.undo())
        assertEquals("hello world", r.text)
        assertEquals(1, r.engine.requests.size)
        assertTrue(r.consents.isEmpty())
    }

    @Test fun aPasswordRestartMidStreamWritesNothing() = run {
        val r = Rig(this)
        consent(r)
        r.startField(textField())
        runCurrent()
        r.engine.script = {
            flow {
                emit(AiEvent.Delta("Hello"))
                delay(1_000)
                emit(AiEvent.Delta(", world."))
                emit(done())
                awaitCancellation()
            }
        }
        r.session.open()
        r.session.rewrite.chooseTone(RewriteMode.FIX)
        runCurrent()
        assertIs<AiSheetState.Streaming>(r.state)

        r.startField(passwordField(), restarting = true)
        assertEquals(AiSheetState.Hidden, r.state)
        assertFalse(r.session.chipAvailable.value)
        advanceTimeBy(5_000)
        runCurrent()
        r.session.rewrite.replace()
        r.session.rewrite.insert()
        assertEquals("hello world", r.text)
        assertTrue(r.commits.isEmpty(), "nothing was written to the host")
        assertEquals(AiSheetState.Hidden, r.state)

        // The chip is gone, and a stale tap opens nothing.
        r.session.open()
        assertEquals(AiSheetState.Hidden, r.state)
        assertEquals(1, r.engine.requests.size)
    }

    @Test fun withoutConsentTheAppConsentScreenIsAskedForOnceAndNothingIsSent() = run {
        val r = Rig(this)
        r.startField(textField())
        runCurrent()
        assertEquals(0L, r.store.current.consentAiAt)

        r.session.open()
        assertEquals(AiSheetState.NeedsConsent, r.state)
        assertEquals(listOf(ConsentKind.AI), r.consents)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf(ConsentKind.AI), r.consents)
        assertTrue(r.engine.requests.isEmpty())
        assertEquals(0, r.tokens.headerCalls)
        assertEquals("hello world", r.text)
    }

    @Test fun theChipIsHiddenForAPasswordField() = run {
        val r = Rig(this)
        consent(r)
        r.startField(passwordField())
        runCurrent()
        assertFalse(r.session.chipAvailable.value)
        r.session.open()
        assertEquals(AiSheetState.Hidden, r.state)
    }

    @Test fun theChipIsHiddenWhenTheBackendIsNotConfigured() = run {
        val unconfigured = Rig(this, configured = false)
        consent(unconfigured)
        unconfigured.startField(textField())
        runCurrent()
        assertFalse(unconfigured.session.chipAvailable.value)
        unconfigured.session.open()
        assertEquals(AiSheetState.Hidden, unconfigured.state)
        assertTrue(unconfigured.consents.isEmpty())
    }

    @Test fun tokensArePrefetchedOncePerKeyboardSessionOnlyWithConsent() = run {
        val r = Rig(this)
        r.startField(textField())
        runCurrent()
        assertEquals(0, r.tokens.prefetchCalls, "no consent, no token traffic")

        r.hide()
        consent(r)
        r.startField(textField())
        runCurrent()
        assertEquals(1, r.tokens.prefetchCalls)
        r.startField(textField(), restarting = true)
        runCurrent()
        assertEquals(1, r.tokens.prefetchCalls)

        r.hide()
        r.startField(passwordField())
        runCurrent()
        assertEquals(1, r.tokens.prefetchCalls, "the chip is not shown in a password field")
        r.hide()
        r.startField(textField())
        runCurrent()
        assertEquals(2, r.tokens.prefetchCalls)
    }

    @Test fun hidingTheKeyboardClosesTheSheetAndCancelsTheStream() = run {
        val r = Rig(this)
        consent(r)
        r.startField(textField())
        runCurrent()
        r.engine.script = { flow { emit(AiEvent.Delta("Hello")); awaitCancellation() } }
        r.session.open()
        r.session.rewrite.chooseTone(RewriteMode.FIX)
        runCurrent()
        assertIs<AiSheetState.Streaming>(r.state)
        r.hide()
        assertEquals(AiSheetState.Hidden, r.state)
        assertEquals("hello world", r.text)
    }
}
