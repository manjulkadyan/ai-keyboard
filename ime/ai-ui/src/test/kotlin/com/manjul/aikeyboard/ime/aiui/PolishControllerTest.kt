/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.aiui

import com.manjul.aikeyboard.core.ai.AiError
import com.manjul.aikeyboard.core.ai.AiEvent
import com.manjul.aikeyboard.core.ai.AiException
import com.manjul.aikeyboard.core.ai.AiPauseGate
import com.manjul.aikeyboard.core.ai.AiRequest
import com.manjul.aikeyboard.core.ai.FakeAiEngine
import com.manjul.aikeyboard.core.ai.OutputScript
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.analytics.PolishMode
import com.manjul.aikeyboard.core.config.DefaultsRemoteConfig
import com.manjul.aikeyboard.ime.editing.DictatedSpan
import com.manjul.aikeyboard.ime.editing.SafeReplacer
import com.manjul.aikeyboard.ime.editing.TextTarget
import com.manjul.aikeyboard.ime.editing.testing.FakeEditor
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A [PolishController] over a [FakeEditor] that holds "Note: " plus a dictation, and a step-fed [FakeAiEngine]. */
internal class PolishRig(val scope: TestScope, dictated: String = "um so hello there") {
    val before = "Note: "
    val editor = FakeEditor("$before$dictated ").apply { hostMoveCursor(before.length + dictated.length + 1) }
    val span = DictatedSpan(before.length until before.length + dictated.length, dictated, "en", 0L)
    val engine = FakeAiEngine()
    var decision = PolicyDecision.Unrestricted
    var generation = 1
    var consent = true
    var consentRequests = 0
    var translateTarget: String? = "es"
    var autoPolish = false
    var autoConsentAt = 0L
    var isPro = false
    var script = OutputScript.MATCH_INPUT
    val clock: () -> Long = { scope.testScheduler.currentTime }
    val pause = AiPauseGate(clock)
    val events = mutableListOf<AnalyticsEvent>()
    val clipboard = mutableListOf<String>()
    val reports = mutableListOf<ReportPayload>()
    val replies = mutableListOf<Channel<Step>>()
    var pausedNotices = 0

    init {
        engine.script = {
            flow {
                val channel = Channel<Step>(Channel.UNLIMITED)
                replies += channel
                for (step in channel) {
                    when (step) {
                        is Step.Event -> emit(step.event)
                        is Step.Fail -> throw AiException(step.error)
                    }
                }
            }
        }
    }

    val controller = PolishController(
        engine = engine,
        replacer = SafeReplacer(editor, clock),
        targetProvider = { editor as TextTarget },
        decision = { decision },
        generation = { generation },
        hasConsent = { consent },
        requestConsent = { consentRequests++ },
        translateTarget = { translateTarget },
        pause = pause,
        config = DefaultsRemoteConfig(),
        analytics = events::add,
        copyToClipboard = clipboard::add,
        report = { reports += it },
        scope = scope.backgroundScope,
        clock = clock,
        autoPolish = { autoPolish },
        autoPolishConsentAt = { autoConsentAt },
        isPro = { isPro },
        outputScript = { script },
        onPaused = { pausedNotices++ },
    )

    val state: AiSheetState get() = controller.state.value
    val chip: PolishChipState? get() = controller.chip.value

    fun reply(vararg steps: Step) {
        steps.forEach { replies.last().trySend(it) }
        scope.runCurrent()
    }

    fun done(polished: String, translation: String = "", rawJson: Boolean = true) {
        if (rawJson) {
            // kb-api relays the model's raw JSON as deltas; the preview reads the field as it grows.
            reply(Step.Event(AiEvent.Delta("{\"polished\":\"")), Step.Event(AiEvent.Delta(polished.replace("\"", "\\\""))))
        }
        reply(Step.Event(AiEvent.Done(4, false, polished, translation, "en")))
        replies.last().close()
        scope.runCurrent()
    }

    fun dictate() {
        controller.onDictationFinished(span)
        scope.runCurrent()
    }

    fun named(name: String) = events.filter { it.name == name }
}

class PolishControllerTest {
    @Test fun `the chip shows after a dictation and hides at 30 s`() = runTest {
        val r = PolishRig(this)
        assertNull(r.chip)
        r.dictate()
        assertEquals(PolishChipState(canTranslate = true, targetLang = "es"), r.chip)
        advanceTimeBy(29_999)
        runCurrent()
        assertNotNull(r.chip)
        advanceTimeBy(2)
        runCurrent()
        assertNull(r.chip)
        r.controller.polish(translate = false)
        runCurrent()
        assertTrue(r.engine.requests.isEmpty(), "an expired chip polishes nothing")
    }

    @Test fun `no translate pair means clean up only`() = runTest {
        val r = PolishRig(this)
        r.translateTarget = null
        r.dictate()
        assertEquals(PolishChipState(canTranslate = false, targetLang = null), r.chip)
        r.controller.polish(translate = true)
        runCurrent()
        assertTrue(r.engine.requests.isEmpty())
    }

    @Test fun `replace changes only the span and undo restores the raw transcript`() = runTest {
        val r = PolishRig(this)
        r.dictate()
        r.controller.polish(translate = false)
        runCurrent()
        assertNull(r.chip, "the sheet replaces the chip")
        val req = assertIs<AiRequest.Polish>(r.engine.requests.single())
        assertEquals("um so hello there", req.transcript)
        assertEquals("en", req.sourceLang)
        assertEquals("en", req.targetLang)
        assertEquals(OutputScript.MATCH_INPUT, req.script)
        assertEquals(1, r.named(EventCatalog.AI_REQUEST).size)

        r.reply(Step.Event(AiEvent.Delta("{\"polished\":\"So, hel")))
        assertEquals("So, hel", assertIs<AiSheetState.Streaming>(r.state).text, "the preview shows the polished field only")
        r.reply(Step.Event(AiEvent.Delta("lo there.\",\"translation\":\"\"")))
        r.done("So, hello there.", rawJson = false)
        val ready = assertIs<AiSheetState.Ready>(r.state)
        assertEquals("So, hello there.", ready.text)
        assertEquals(PolishMode.CLEAN, ready.polish)
        assertEquals("Note: um so hello there ", r.editor.text, "the preview stays in the sheet")

        r.controller.replace()
        assertEquals("Note: So, hello there. ", r.editor.text)
        assertIs<AiSheetState.Replaced>(r.state)
        val done = r.named(EventCatalog.AI_POLISH_DONE).single()
        assertEquals("clean", done.params["mode"])
        assertEquals(false, done.params["auto"])

        assertTrue(r.controller.undo())
        assertEquals("Note: um so hello there ", r.editor.text)
        assertEquals(AiSheetState.Hidden, r.state)
    }

    @Test fun `an edit inside the span before Replace writes nothing`() = runTest {
        val r = PolishRig(this)
        r.dictate()
        r.controller.polish(translate = false)
        runCurrent()
        r.done("So, hello there.")
        r.editor.hostReplace(6, 8, "uh")
        val before = r.editor.text
        r.controller.replace()
        assertIs<AiSheetState.TextChanged>(r.state)
        assertEquals(before, r.editor.text)
        assertTrue(r.named(EventCatalog.AI_POLISH_DONE).isEmpty())
        assertEquals("text_changed", r.named(EventCatalog.AI_ERROR).single().params["reason"])
    }

    @Test fun `translate sends the pair target and the output script, and shows the translation`() = runTest {
        val r = PolishRig(this)
        r.script = OutputScript.ROMAN
        r.dictate()
        r.controller.polish(translate = true)
        runCurrent()
        val req = assertIs<AiRequest.Polish>(r.engine.requests.single())
        assertEquals("es", req.targetLang)
        assertEquals("en", req.sourceLang)
        assertEquals(OutputScript.ROMAN, req.script)
        r.done("So, hello there.", translation = "Así que, hola.")
        val ready = assertIs<AiSheetState.Ready>(r.state)
        assertEquals("Así que, hola.", ready.text)
        assertEquals(PolishMode.CLEAN_TRANSLATE, ready.polish)
        r.controller.replace()
        assertEquals("Note: Así que, hola. ", r.editor.text)
        assertEquals("clean_translate", r.named(EventCatalog.AI_POLISH_DONE).single().params["mode"])
    }

    @Test fun `quota during auto polish keeps the raw text, shows no sheet and pauses auto polish`() = runTest {
        val r = PolishRig(this)
        r.autoPolish = true
        r.autoConsentAt = 1L
        r.isPro = true
        r.dictate()
        assertEquals(1, r.engine.requests.size, "auto polish runs without a tap")
        assertEquals(AiSheetState.Hidden, r.state)
        r.reply(Step.Fail(AiError.Quota(resetsAtEpochMs = 86_400_000L)))
        assertEquals(AiSheetState.Hidden, r.state)
        assertEquals("Note: um so hello there ", r.editor.text)
        assertTrue(r.controller.quotaPaused)
        assertEquals("polish", r.named(EventCatalog.AI_QUOTA_HIT).single().params["feature"])

        r.dictate()
        assertEquals(1, r.engine.requests.size, "auto polish stays paused")
        assertNotNull(r.chip, "the manual chip is still offered")
    }

    @Test fun `the auto polish pause ends at the next UTC day`() = runTest {
        val r = PolishRig(this)
        r.autoPolish = true
        r.autoConsentAt = 1L
        r.isPro = true
        r.dictate()
        r.reply(Step.Fail(AiError.Quota(resetsAtEpochMs = 86_400_000L)))
        assertTrue(r.controller.quotaPaused)
        advanceTimeBy(86_400_000L)
        runCurrent()
        assertFalse(r.controller.quotaPaused)
    }

    @Test fun `auto polish replaces the span by itself and logs auto`() = runTest {
        val r = PolishRig(this)
        r.autoPolish = true
        r.autoConsentAt = 1L
        r.isPro = true
        r.dictate()
        assertNull(r.chip)
        r.done("So, hello there.")
        assertEquals("Note: So, hello there. ", r.editor.text)
        assertEquals(AiSheetState.Hidden, r.state, "auto polish never covers the keys with the sheet")
        assertTrue(r.controller.autoUndo.value, "a light Undo chip is offered instead")
        assertEquals(true, r.named(EventCatalog.AI_POLISH_DONE).single().params["auto"])
        assertTrue(r.controller.undo())
        assertEquals("Note: um so hello there ", r.editor.text)
        assertFalse(r.controller.autoUndo.value)
    }

    @Test fun `the auto polish undo chip goes after the undo window`() = runTest {
        val r = PolishRig(this)
        r.autoPolish = true
        r.autoConsentAt = 1L
        r.isPro = true
        r.dictate()
        r.done("So, hello there.")
        assertTrue(r.controller.autoUndo.value)
        advanceTimeBy(PolishController.UNDO_WINDOW_MS + 1)
        runCurrent()
        assertFalse(r.controller.autoUndo.value)
        assertFalse(r.controller.undo())
        assertEquals("Note: So, hello there. ", r.editor.text)
    }

    private fun TestScope.readyRig(): PolishRig {
        val r = PolishRig(this)
        r.dictate()
        r.controller.polish(translate = false)
        runCurrent()
        r.done("So, hello there.")
        assertIs<AiSheetState.Ready>(r.state)
        return r
    }

    @Test fun `retry on an output asks for consent again once it was withdrawn`() = runTest {
        val r = readyRig()
        r.consent = false
        r.controller.retry()
        runCurrent()
        assertEquals(1, r.engine.requests.size, "nothing is sent without consent")
        assertEquals(AiSheetState.NeedsConsent, r.state)
        assertEquals(1, r.consentRequests)
    }

    @Test fun `retry on an output sends nothing while AI is paused or the field changed`() = runTest {
        val paused = readyRig()
        paused.pause.pauseFor(AiPauseGate.PAUSED_MS)
        paused.controller.retry()
        runCurrent()
        assertEquals(1, paused.engine.requests.size)
        assertEquals(AiSheetState.Hidden, paused.state)

        val moved = readyRig()
        moved.generation = 2
        moved.controller.retry()
        runCurrent()
        assertEquals(1, moved.engine.requests.size)
        assertEquals(AiSheetState.Hidden, moved.state)
    }

    @Test fun `Paused from polish tells the rewrite chip to re-check availability`() = runTest {
        val r = PolishRig(this)
        r.dictate()
        r.controller.polish(translate = false)
        runCurrent()
        r.reply(Step.Fail(AiError.Paused))
        assertTrue(r.pause.isPaused)
        assertEquals(1, r.pausedNotices)
    }

    @Test fun `auto polish never runs without Pro or in a sensitive field`() = runTest {
        val r = PolishRig(this)
        r.autoPolish = true
        r.autoConsentAt = 1L
        r.isPro = false
        r.dictate()
        assertTrue(r.engine.requests.isEmpty())
        assertNotNull(r.chip)

        r.isPro = true
        r.decision = AiRig.PASSWORD
        r.dictate()
        assertTrue(r.engine.requests.isEmpty())
        assertNull(r.chip, "no chip either where AI is not allowed")
    }

    @Test fun `typing or moving the cursor out of the span hides the chip`() = runTest {
        val r = PolishRig(this)
        r.dictate()
        r.controller.onCursorOrTyping(24)
        assertNotNull(r.chip, "the cursor after the trailing space is still at the span")
        r.controller.onCursorOrTyping(10)
        assertNotNull(r.chip)
        r.controller.onCursorOrTyping(25)
        assertNull(r.chip)

        r.dictate()
        r.controller.onCursorOrTyping(2)
        assertNull(r.chip)
    }

    @Test fun `without consent the chip asks for consent and sends nothing`() = runTest {
        val r = PolishRig(this)
        r.consent = false
        r.dictate()
        r.controller.polish(translate = false)
        runCurrent()
        assertEquals(AiSheetState.NeedsConsent, r.state)
        assertEquals(1, r.consentRequests)
        assertTrue(r.engine.requests.isEmpty())
    }

    @Test fun `a restriction mid stream drops everything and writes nothing`() = runTest {
        val r = PolishRig(this)
        r.dictate()
        r.controller.polish(translate = false)
        runCurrent()
        r.decision = AiRig.PASSWORD
        r.reply(Step.Event(AiEvent.Delta("{\"polished\":\"So")))
        assertEquals(AiSheetState.Hidden, r.state)
        r.controller.replace()
        assertEquals("Note: um so hello there ", r.editor.text)
        assertNull(r.chip)
    }

    @Test fun `a field change hides the chip and a late dictation of the old field is ignored`() = runTest {
        val r = PolishRig(this)
        r.dictate()
        r.generation = 2
        r.controller.onFieldChanged()
        assertNull(r.chip)
        r.controller.polish(translate = false)
        runCurrent()
        assertTrue(r.engine.requests.isEmpty())
    }

    @Test fun `report sends the polish prompt id`() = runTest {
        val r = PolishRig(this)
        r.dictate()
        r.controller.polish(translate = false)
        runCurrent()
        r.done("So, hello there.")
        r.controller.openReport()
        assertEquals(AiSheetState.Reporting(com.manjul.aikeyboard.core.ai.RewriteMode.FIX, PolishMode.CLEAN), r.state)
        r.controller.submitReport(ReportReason.WRONG, includeText = false)
        runCurrent()
        val payload = r.reports.single()
        assertEquals(PolishController.PROMPT_POLISH, payload.promptId)
        assertNull(payload.text)
        assertEquals("polish", r.named(EventCatalog.AI_OUTPUT_REPORTED).single().params["tone"])
    }

    @Test fun `no event or state carries user text`() = runTest {
        val r = PolishRig(this)
        r.dictate()
        r.controller.polish(translate = false)
        runCurrent()
        r.done("So, hello there.")
        r.controller.replace()
        for (event in r.events) {
            for (value in event.params.values) assertFalse(value.toString().contains("hello"), event.name)
        }
        assertFalse(r.span.toString().contains("hello"))
        assertFalse(r.state.toString().contains("hello"))
    }
}
