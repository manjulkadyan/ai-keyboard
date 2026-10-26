/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.ai

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import com.manjul.aikeyboard.core.ai.AiEvent
import com.manjul.aikeyboard.core.ai.AiLru
import com.manjul.aikeyboard.core.ai.AiPauseGate
import com.manjul.aikeyboard.core.ai.AiRequest
import com.manjul.aikeyboard.core.ai.FakeAiEngine
import com.manjul.aikeyboard.core.ai.FakeTokenSource
import com.manjul.aikeyboard.core.ai.OutputScript
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.config.DefaultsRemoteConfig
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.fork.policy.ImePolicyController
import com.manjul.aikeyboard.fork.voice.ImeTextTarget
import com.manjul.aikeyboard.ime.aiui.AiSheetState
import com.manjul.aikeyboard.ime.aiui.PolishChipState
import com.manjul.aikeyboard.ime.editing.DictatedSpanRecorder
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Dictate -> ✦ Polish in the keyboard, end to end over a real EditText connection: a dictation commits into the
 * field the way VoiceImeSession does (its [DictatedSpanRecorder] reports the span), then [AiImeSession.polish] runs.
 * The engine is a fake; nothing leaves the test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PolishFlowTest {
    private val app = RuntimeEnvironment.getApplication()

    inner class Rig(private val scope: TestScope, initial: String = "Note: ") {
        val edit = EditText(app).apply {
            setText(initial)
            setSelection(initial.length)
        }
        val ic: InputConnection = edit.onCreateInputConnection(EditorInfo())
        val engine = FakeAiEngine()
        val events = mutableListOf<AnalyticsEvent>()
        val store = FeatureSettingsStore(app, StandardTestDispatcher(scope.testScheduler))
        val policy = ImePolicyController(store, keyguard = { false }, forcedPackages = { emptySet() }, scope = scope.backgroundScope)
        var pro = false
        val session = AiImeSession(
            connection = { ic },
            decision = { policy.decision.value },
            generation = policy.generation,
            settings = store,
            graph = AiAppGraph(
                config = DefaultsRemoteConfig(),
                tokens = FakeTokenSource(),
                engine = engine,
                lru = AiLru(),
                pause = AiPauseGate { scope.testScheduler.currentTime },
                report = {},
                isConfigured = true,
            ),
            launchConsent = {},
            isPro = { pro },
            scope = scope.backgroundScope,
            clock = { scope.testScheduler.currentTime },
            analytics = events::add,
        ).also { it.bindTo(policy) }

        // VoiceImeSession's recorder over the host field.
        private val recorder = DictatedSpanRecorder { ImeTextTarget({ ic }, { false }, {}) }
        val text get() = edit.text.toString()

        fun startField() {
            val info = EditorInfo().apply {
                packageName = "some.app"
                inputType = InputType.TYPE_CLASS_TEXT
            }
            session.onStartInput(false)
            policy.onStartInput(info, false)
            session.onStartInputView()
        }

        /** A dictation: the voice controller commits the final transcript and a space, then reports the span. */
        fun dictate(transcript: String) {
            recorder.begin()
            ic.commitText("$transcript ", 1)
            val span = assertNotNull(recorder.finish(transcript, "en", scope.testScheduler.currentTime))
            session.onDictationFinished(span)
            session.onCursorOrTyping(edit.selectionStart)
            scope.runCurrent()
        }
    }

    private fun run(block: suspend TestScope.() -> Unit) = runTest(StandardTestDispatcher()) { block() }

    @Test fun polishReplaceRewritesOnlyTheDictatedSpan() = run {
        val r = Rig(this)
        r.store.update { it.copy(consentAiAt = 1L) }
        runCurrent()
        r.startField()
        runCurrent()

        r.dictate("um so hello there")
        assertEquals("Note: um so hello there ", r.text)
        assertEquals(PolishChipState(canTranslate = false, targetLang = null), r.session.polish.chip.value)

        r.engine.respondWith(
            AiEvent.Delta("{\"polished\":\"So, hello there.\"}"),
            AiEvent.Done(quotaLeft = 9, trimmed = false, polished = "So, hello there.", translation = null, sourceLangDetected = "en"),
        )
        r.session.polish(translate = false)
        runCurrent()
        val request = assertIs<AiRequest.Polish>(r.engine.requests.single())
        assertEquals("um so hello there", request.transcript, "only the span is sent, never \"Note: \"")
        assertEquals(OutputScript.MATCH_INPUT, request.script)
        assertEquals("So, hello there.", assertIs<AiSheetState.Ready>(r.session.polish.state.value).text)
        assertEquals("Note: um so hello there ", r.text, "the preview stays in the sheet")

        r.session.polish.replace()
        assertEquals("Note: So, hello there. ", r.text)
        assertTrue(r.text.startsWith("Note: So, hello there."))
        assertTrue(r.events.any { it.name == EventCatalog.AI_POLISH_DONE })

        assertTrue(r.session.polish.undo())
        assertEquals("Note: um so hello there ", r.text)
    }

    @Test fun autoPolishWithoutProNeverCallsTheEngine() = run {
        val r = Rig(this)
        r.store.update { it.copy(consentAiAt = 1L, consentAutoPolishAt = 1L, autoPolish = true) }
        runCurrent()
        r.startField()
        runCurrent()

        r.dictate("um so hello there")
        assertTrue(r.engine.requests.isEmpty(), "auto-polish is Pro only")
        assertEquals("Note: um so hello there ", r.text)
        assertNotNull(r.session.polish.chip.value, "the manual chip is offered instead")
    }
}
