/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.voice

import android.Manifest
import android.media.AudioManager
import android.text.InputType
import android.text.Selection
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.core.voice.EngineKind
import com.manjul.aikeyboard.core.voice.SpeechEngine
import com.manjul.aikeyboard.core.voice.platform.RecognizerCapabilities
import com.manjul.aikeyboard.fork.policy.ImePolicyController
import com.manjul.aikeyboard.ime.voiceui.VoiceUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The IME path of voice typing, from the mic key to the host field. FlorisImeService cannot run under Robolectric
 * (native library), so this drives [VoiceImeSession] the way the service does: the real policy controller gets
 * onStartInput, a real EditText input connection receives the edits and its selection is fed back as
 * onUpdateSelection would.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class VoiceImeSessionTest {
    private val app = RuntimeEnvironment.getApplication()

    private class Engine(override val kind: EngineKind) : SpeechEngine {
        override val supportsPartials = true
        var listener: SpeechEngine.Listener? = null
        var destroyed = false
        override fun start(lang: String, listener: SpeechEngine.Listener) { this.listener = listener }
        override fun stop() {}
        override fun cancel() {}
        override fun destroy() { destroyed = true }
    }

    private object Caps : RecognizerCapabilities {
        override val sdkInt = 34
        override fun isRecognitionAvailable() = true
        override fun isOnDeviceAvailable() = true
        override suspend fun onDeviceSupports(lang: String): Boolean? = true
        override fun whisperModelReady(lang: String) = false
        override fun whisperLangEnabled(lang: String) = false
    }

    private inner class Rig(scope: TestScope, initial: String = "") {
        val edit = EditText(app).apply {
            setText(initial)
            setSelection(initial.length)
        }
        val commits = mutableListOf<String>()
        var deletes = 0
        val ic: InputConnection = object : InputConnectionWrapper(edit.onCreateInputConnection(EditorInfo()), false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                commits += text.toString()
                return super.commitText(text, newCursorPosition)
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                deletes++
                return super.deleteSurroundingText(beforeLength, afterLength)
            }
        }
        val engines = mutableListOf<Engine>()
        val events = mutableListOf<AnalyticsEvent>()
        val store = FeatureSettingsStore(app, StandardTestDispatcher(scope.testScheduler))
        val policy = ImePolicyController(store, keyguard = { false }, forcedPackages = { emptySet() }, scope = scope.backgroundScope)
        var ownsComposing = false
        val audioManager: AudioManager = app.getSystemService(AudioManager::class.java)
        val session = VoiceImeSession(
            context = app,
            policy = policy,
            store = store,
            analytics = { events += it },
            connection = { ic },
            isRawInputEditor = { false },
            setOwnsComposing = { ownsComposing = it },
            lang = { "en-US" },
            scope = scope.backgroundScope,
            clock = { NOW },
            caps = Caps,
            engineFactory = { kind, _ -> Engine(kind).also { engines += it } },
            online = { true },
        )
        val engine get() = engines.last()
        val text get() = edit.text.toString()

        /** What the framework would pass to onUpdateSelection for the current field state. */
        fun report() {
            val e = edit.text
            session.onSelectionUpdate(
                Selection.getSelectionStart(e),
                Selection.getSelectionEnd(e),
                BaseInputConnection.getComposingSpanStart(e),
                BaseInputConnection.getComposingSpanEnd(e),
            )
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

    private fun run(block: suspend TestScope.() -> Unit) = runTest(StandardTestDispatcher()) {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        block()
    }

    private fun TestScope.listen(r: Rig, partial: String) {
        r.policy.onStartInput(textField(), false)
        r.session.controller.toggle()
        runCurrent()
        r.engine.listener!!.onReady()
        r.engine.listener!!.onPartial(partial)
        runCurrent()
        r.report()
    }

    @Test fun passwordRestartRemovesOurPartialWithoutCommittingIt() = run {
        val r = Rig(this)
        listen(r, "hel")
        assertEquals("hel", r.text)
        assertEquals(0, BaseInputConnection.getComposingSpanStart(r.edit.text))
        assertTrue(r.ownsComposing)

        r.policy.onStartInput(passwordField(), true)
        runCurrent()

        // R24: our own verified composing region is removed; nothing is deleted outside it.
        assertEquals("", r.text)
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(r.edit.text))
        assertTrue(r.commits.isEmpty())
        assertEquals(0, r.deletes)
        assertFalse(r.ownsComposing)
        assertEquals(VoiceUiState.Hidden, r.session.controller.ui.value)
        assertTrue(r.engine.destroyed)
        assertNotNull(shadowOf(r.audioManager).lastAbandonedAudioFocusRequest)
        assertEquals("field_change", r.events.single { it.name == "voice_session_end" }.params["end_reason"])

        // A late result for the old field changes nothing.
        r.engine.listener!!.onFinal("hello")
        runCurrent()
        assertEquals("", r.text)
        assertTrue(r.commits.isEmpty())
    }

    @Test fun cancelLeavesTheFieldAloneWhenTheTextBeforeTheCursorIsNoLongerOurs() = run {
        val r = Rig(this, initial = "abc ")
        listen(r, "hel")
        assertEquals("abc hel", r.text)
        // The host moved the cursor; its selection update has not arrived yet.
        Selection.setSelection(r.edit.text, 2)

        r.policy.onStartInput(passwordField(), true)
        runCurrent()

        assertEquals("abc hel", r.text)
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(r.edit.text))
        assertEquals(0, r.deletes)
        assertTrue(r.commits.isEmpty())
        assertFalse(r.ownsComposing)
    }

    @Test fun punctuationAfterTheHostMovedTheCursorDeletesNothing() = run {
        val r = Rig(this, initial = "ab cd")
        r.policy.onStartInput(textField(), false)
        r.session.controller.toggle()
        runCurrent()
        r.engine.listener!!.onReady()
        r.engine.listener!!.onFinal("hello")
        runCurrent()
        r.report()
        assertEquals("ab cdhello ", r.text)

        // The host moves the cursor to just after its own space; no composing region before or after.
        Selection.setSelection(r.edit.text, 3)
        r.report()

        advanceTimeBy(RESTART_MS)
        runCurrent()
        r.engine.listener!!.onReady()
        r.engine.listener!!.onFinal(", x")
        runCurrent()

        assertEquals(0, r.deletes)
        assertTrue(r.text.startsWith("ab , x"), r.text)
        assertTrue(r.text.endsWith("cdhello "), r.text)
    }

    @Test fun hostEditDuringAPartialDeletesNoHostText() = run {
        val r = Rig(this, initial = "abc ")
        listen(r, "hel")
        assertEquals("abc hel", r.text)

        // The app inserts text in front of our region; the region shifts away from where we put it.
        r.edit.text.insert(0, "X")
        r.report()
        r.engine.listener!!.onPartial("hello")
        runCurrent()
        r.report()

        // The region moved intact (same text, cursor at its end): it is still ours and is updated in place.
        assertEquals("Xabc hello", r.text)
        assertEquals(0, r.deletes)
        assertTrue(r.commits.isEmpty())
    }

    @Test fun ownPartialUpdatesKeepReplacingTheRegion() = run {
        val r = Rig(this, initial = "abc ")
        listen(r, "hel")
        r.engine.listener!!.onPartial("hello")
        r.engine.listener!!.onPartial("hello wor")
        runCurrent()
        r.report()
        assertEquals("abc hello wor", r.text)
        r.session.controller.toggle()
        r.engine.listener!!.onFinal("hello world")
        runCurrent()
        r.report()
        assertEquals("abc hello world ", r.text)
        assertEquals(listOf("hello world "), r.commits)
        assertFalse(r.ownsComposing)
    }

    @Test fun finishInputViewStopsAndAbandonsFocus() = run {
        val r = Rig(this)
        listen(r, "hi")
        assertTrue(r.session.controller.ui.value is VoiceUiState.Listening)

        r.policy.onFinishInputView()
        r.session.onFinishInputView()
        runCurrent()

        assertEquals(VoiceUiState.Hidden, r.session.controller.ui.value)
        assertTrue(r.engine.destroyed)
        assertNotNull(shadowOf(r.audioManager).lastAbandonedAudioFocusRequest)
        // A cancel, like a field change: the unfinished partial is ours and is removed (R24).
        assertEquals("", r.text)
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(r.edit.text))
        assertEquals(0, r.deletes)
    }

    @Test fun firstDictationIsPersisted() = run {
        val r = Rig(this)
        listen(r, "hi")
        r.session.controller.toggle()
        r.engine.listener!!.onFinal("hi")
        runCurrent()
        assertEquals(1, r.events.count { it.name == "first_dictation" })
        assertEquals(NOW, r.store.flow.first().firstDictationAt)
    }

    private companion object {
        const val NOW = 1_234L

        /** Past VoiceTimings.restartDelayMs, short of the silence end. */
        const val RESTART_MS = 300L
    }
}
