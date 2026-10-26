/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.aiui

import com.manjul.aikeyboard.core.ai.AiError
import com.manjul.aikeyboard.core.ai.AiEvent
import com.manjul.aikeyboard.core.ai.AiException
import com.manjul.aikeyboard.core.ai.AiLru
import com.manjul.aikeyboard.core.ai.AiPauseGate
import com.manjul.aikeyboard.core.ai.AiRequest
import com.manjul.aikeyboard.core.ai.FakeAiEngine
import com.manjul.aikeyboard.core.ai.RewriteMode
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.config.ConfigKeys
import com.manjul.aikeyboard.core.config.DefaultsRemoteConfig
import com.manjul.aikeyboard.core.config.RemoteConfig
import com.manjul.aikeyboard.ime.editing.SafeReplacer
import com.manjul.aikeyboard.ime.editing.TextTarget
import com.manjul.aikeyboard.ime.editing.testing.FakeEditor
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import com.manjul.aikeyboard.ime.policy.SensitivityReason
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** One scripted reply step: an event, or a failure. */
internal sealed interface Step {
    data class Event(val event: AiEvent) : Step
    data class Fail(val error: AiError) : Step
}

/**
 * An [AiActionController] over a [FakeEditor] host and a [FakeAiEngine] whose replies the test feeds one step at a
 * time through [reply], in virtual time. Shared with [AiActionControllerPropertyTest].
 */
internal class AiRig(
    val scope: TestScope,
    host: String = "helo wrld",
    val config: RemoteConfig = DefaultsRemoteConfig(),
) {
    val editor = FakeEditor(host)
    val engine = FakeAiEngine()
    var decision = PolicyDecision.Unrestricted
    var generation = 1
    var consent = true
    var consentRequests = 0
    var translateTarget: String? = "es"
    var targetAvailable = true
    val lru = AiLru()
    val clock: () -> Long = { scope.testScheduler.currentTime }
    val pause = AiPauseGate(clock)
    val events = mutableListOf<AnalyticsEvent>()
    val clipboard = mutableListOf<String>()
    val reports = mutableListOf<ReportPayload>()
    val replacer = SafeReplacer(editor, clock)

    /** Open reply channels, one per engine call, newest last. */
    val replies = mutableListOf<Channel<Step>>()

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

    val controller = AiActionController(
        engine = engine,
        replacer = replacer,
        targetProvider = { if (targetAvailable) editor as TextTarget else null },
        decision = { decision },
        generation = { generation },
        hasConsent = { consent },
        requestConsent = { consentRequests++ },
        locale = { "en" },
        translateTarget = { translateTarget },
        lru = lru,
        pause = pause,
        config = config,
        analytics = events::add,
        copyToClipboard = clipboard::add,
        report = { reports += it },
        scope = scope.backgroundScope,
        clock = clock,
    )

    val state: AiSheetState get() = controller.state.value

    /** Sends [steps] on the newest reply channel and lets the controller handle them. */
    fun reply(vararg steps: Step) {
        val channel = replies.last()
        steps.forEach { channel.trySend(it) }
        scope.runCurrent()
    }

    fun delta(text: String) = reply(Step.Event(AiEvent.Delta(text)))

    fun done(trimmed: Boolean = false) {
        reply(Step.Event(AiEvent.Done(quotaLeft = 5, trimmed = trimmed, polished = null, translation = null, sourceLangDetected = null)))
        replies.last().close()
        scope.runCurrent()
    }

    fun fail(error: AiError) {
        reply(Step.Fail(error))
    }

    /** Opens the sheet and picks [mode]; the request is then waiting for replies. */
    fun start(mode: RewriteMode = RewriteMode.FIX) {
        controller.open()
        controller.chooseTone(mode)
        scope.runCurrent()
    }

    /** A full rewrite of the field to [output]. */
    fun rewrite(output: String, mode: RewriteMode = RewriteMode.FIX) {
        start(mode)
        delta(output)
        done()
    }

    fun named(name: String) = events.filter { it.name == name }

    companion object {
        val PASSWORD = PolicyDecision.Unrestricted.copy(
            sensitive = true,
            reasons = setOf(SensitivityReason.PASSWORD),
            allowLearning = false,
            allowAi = false,
            allowCloud = false,
        )
    }
}

class AiActionControllerTest {
    @Test fun `without consent open asks for it and nothing is captured or sent`() = runTest {
        val rig = AiRig(this)
        rig.consent = false
        rig.controller.open()
        runCurrent()
        assertEquals(AiSheetState.NeedsConsent, rig.state)
        assertEquals(1, rig.consentRequests)
        rig.controller.chooseTone(RewriteMode.FIX)
        runCurrent()
        assertTrue(rig.engine.requests.isEmpty())
        assertTrue(rig.named(EventCatalog.AI_REQUEST).isEmpty())
        assertEquals(2, rig.consentRequests)
    }

    @Test fun `open with consent offers the tones, translate only with a target`() = runTest {
        val rig = AiRig(this)
        rig.controller.open()
        val tones = assertIs<AiSheetState.ChooseTone>(rig.state)
        assertEquals(RewriteMode.entries.toList(), tones.tones)
        assertEquals("es", tones.translateTarget)
        rig.controller.dismiss()
        rig.translateTarget = null
        rig.controller.open()
        val noTranslate = assertIs<AiSheetState.ChooseTone>(rig.state)
        assertFalse(RewriteMode.TRANSLATE in noTranslate.tones)
        assertTrue(rig.engine.requests.isEmpty())
    }

    @Test fun `deltas build the preview, then Ready`() = runTest {
        val rig = AiRig(this)
        rig.start()
        assertEquals(AiSheetState.Streaming(RewriteMode.FIX, "", stillWorking = false), rig.state)
        val req = assertIs<AiRequest.Rewrite>(rig.engine.requests.single())
        assertEquals("helo wrld", req.text)
        assertEquals("en", req.locale)
        assertNull(req.targetLang)
        rig.delta("Hello")
        assertEquals(AiSheetState.Streaming(RewriteMode.FIX, "Hello", stillWorking = false), rig.state)
        rig.reply(Step.Event(AiEvent.StillWorking))
        assertEquals(AiSheetState.Streaming(RewriteMode.FIX, "Hello", stillWorking = true), rig.state)
        rig.delta(" world")
        rig.done()
        assertEquals(AiSheetState.Ready(RewriteMode.FIX, "Hello world", trimmed = false), rig.state)
        assertEquals("helo wrld", rig.editor.text)
    }

    @Test fun `translate sends the target language`() = runTest {
        val rig = AiRig(this)
        rig.start(RewriteMode.TRANSLATE)
        assertEquals("es", assertIs<AiRequest.Rewrite>(rig.engine.requests.single()).targetLang)
    }

    @Test fun `Replace writes the host and logs ai_rewrite_done replace`() = runTest {
        val rig = AiRig(this)
        rig.rewrite("Hello world")
        advanceTimeBy(1_200)
        rig.controller.replace()
        assertEquals("Hello world", rig.editor.text)
        assertEquals(AiSheetState.Replaced(RewriteMode.FIX), rig.state)
        val done = rig.named(EventCatalog.AI_REWRITE_DONE).single()
        assertEquals("replace", done.params["action"])
        assertEquals("fix", done.params["tone"])
        assertTrue(rig.named(EventCatalog.AI_ERROR).isEmpty())
    }

    @Test fun `undo within 5 s restores the original`() = runTest {
        val rig = AiRig(this)
        rig.rewrite("Hello world")
        rig.controller.replace()
        advanceTimeBy(4_000)
        assertTrue(rig.controller.undo())
        assertEquals("helo wrld", rig.editor.text)
        assertEquals(AiSheetState.Hidden, rig.state)
    }

    @Test fun `the undo offer ends after 5 s and undo then does nothing`() = runTest {
        val rig = AiRig(this)
        rig.rewrite("Hello world")
        rig.controller.replace()
        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(AiSheetState.Hidden, rig.state)
        assertFalse(rig.controller.undo())
        assertEquals("Hello world", rig.editor.text)
    }

    @Test fun `a host edit during streaming makes Replace refuse, then Insert works`() = runTest {
        val rig = AiRig(this)
        rig.start()
        rig.delta("Hello world")
        rig.editor.hostReplace(0, 4, "help")
        rig.done()
        rig.controller.replace()
        assertEquals("help wrld", rig.editor.text)
        assertEquals(AiSheetState.TextChanged("Hello world"), rig.state)
        assertEquals("text_changed", rig.named(EventCatalog.AI_ERROR).single().params["reason"])
        assertTrue(rig.named(EventCatalog.AI_REWRITE_DONE).isEmpty())
        rig.controller.insert()
        assertEquals("help wrldHello world", rig.editor.text)
        assertEquals(AiSheetState.Hidden, rig.state)
        assertEquals("insert", rig.named(EventCatalog.AI_REWRITE_DONE).single().params["action"])
    }

    @Test fun `onRestricted mid-stream hides, drops late deltas and clears the LRU`() = runTest {
        val rig = AiRig(this)
        rig.rewrite("Cached")
        rig.controller.dismiss()
        assertEquals(1, rig.lru.size)
        rig.start(RewriteMode.FORMAL)
        rig.delta("Dear")
        rig.decision = AiRig.PASSWORD
        rig.controller.onRestricted()
        assertEquals(AiSheetState.Hidden, rig.state)
        rig.delta(" Sir")
        rig.done()
        assertEquals(AiSheetState.Hidden, rig.state)
        assertEquals("helo wrld", rig.editor.text)
        assertEquals(0, rig.lru.size)
        assertFalse(rig.controller.isAvailable.value)
    }

    @Test fun `a restriction seen on a delta cancels without a callback`() = runTest {
        val rig = AiRig(this)
        rig.start()
        rig.delta("Hel")
        rig.decision = AiRig.PASSWORD
        rig.delta("lo")
        assertEquals(AiSheetState.Hidden, rig.state)
        rig.controller.replace()
        assertEquals("helo wrld", rig.editor.text)
        assertEquals(0, rig.lru.size)
    }

    @Test fun `a field generation bump mid-stream hides and writes nothing`() = runTest {
        val rig = AiRig(this)
        rig.rewrite("Cached")
        rig.controller.dismiss()
        rig.start(RewriteMode.SHORTER)
        rig.delta("Hi")
        rig.generation++
        rig.delta(" there")
        rig.done()
        assertEquals(AiSheetState.Hidden, rig.state)
        rig.controller.replace()
        rig.controller.insert()
        assertEquals("helo wrld", rig.editor.text)
        assertEquals(0, rig.lru.size)
    }

    @Test fun `onFieldChanged mid-stream hides and clears the LRU`() = runTest {
        val rig = AiRig(this)
        rig.rewrite("Cached")
        rig.controller.dismiss()
        rig.start(RewriteMode.SHORTER)
        rig.delta("Hi")
        rig.generation++
        rig.controller.onFieldChanged()
        assertEquals(AiSheetState.Hidden, rig.state)
        rig.done()
        assertEquals(AiSheetState.Hidden, rig.state)
        assertEquals("helo wrld", rig.editor.text)
        assertEquals(0, rig.lru.size)
    }

    @Test fun `a generation bump between Ready and Replace writes nothing`() = runTest {
        val rig = AiRig(this)
        rig.rewrite("Hello world")
        rig.generation++
        rig.controller.replace()
        assertEquals("helo wrld", rig.editor.text)
        assertEquals(AiSheetState.Hidden, rig.state)
    }

    @Test fun `Quota shows the quota card and logs one ai_quota_hit`() = runTest {
        val rig = AiRig(this)
        rig.start()
        rig.fail(AiError.Quota(resetsAtEpochMs = 7_200_000))
        assertEquals(AiSheetState.Error(AiError.Quota(7_200_000), canRetry = false), rig.state)
        assertEquals("rewrite", rig.named(EventCatalog.AI_QUOTA_HIT).single().params["feature"])
        assertTrue(rig.named(EventCatalog.AI_ERROR).isEmpty())
    }

    @Test fun `Paused hides AI for one hour`() = runTest {
        val rig = AiRig(this)
        assertTrue(rig.controller.isAvailable.value)
        rig.start()
        rig.fail(AiError.Paused)
        assertFalse(rig.controller.isAvailable.value)
        assertEquals("paused", rig.named(EventCatalog.AI_ERROR).single().params["reason"])
        advanceTimeBy(3_599_000)
        assertFalse(rig.controller.isAvailable.value)
        rig.controller.dismiss()
        rig.controller.open()
        assertEquals(AiSheetState.Hidden, rig.state)
        advanceTimeBy(2_000)
        runCurrent()
        assertTrue(rig.controller.isAvailable.value)
    }

    @Test fun `ai_enabled_global false makes AI unavailable`() = runTest {
        val rig = AiRig(this, config = DefaultsRemoteConfig(mapOf(ConfigKeys.AI_ENABLED_GLOBAL to false)))
        assertFalse(rig.controller.isAvailable.value)
        rig.controller.open()
        assertEquals(AiSheetState.Hidden, rig.state)
    }

    @Test fun `a re-tap of the same tone and text is served from the LRU`() = runTest {
        val rig = AiRig(this)
        rig.rewrite("Hello world")
        rig.controller.dismiss()
        rig.controller.open()
        rig.controller.chooseTone(RewriteMode.FIX)
        runCurrent()
        assertEquals(AiSheetState.Ready(RewriteMode.FIX, "Hello world", trimmed = false), rig.state)
        assertEquals(1, rig.engine.requests.size)
        assertEquals(1, rig.named(EventCatalog.AI_REQUEST).size)
        rig.controller.replace()
        assertEquals("Hello world", rig.editor.text)
    }

    @Test fun `Retry after Ready asks again with a new request id`() = runTest {
        val rig = AiRig(this)
        rig.rewrite("Hello world")
        rig.controller.retry()
        runCurrent()
        assertEquals(2, rig.engine.requests.size)
        assertTrue(rig.engine.requests[0].clientReqId != rig.engine.requests[1].clientReqId)
    }

    @Test fun `Retry after a timeout reuses the request id`() = runTest {
        val rig = AiRig(this)
        rig.start()
        rig.fail(AiError.Timeout)
        assertEquals(AiSheetState.Error(AiError.Timeout, canRetry = true), rig.state)
        rig.controller.retry()
        runCurrent()
        assertEquals(2, rig.engine.requests.size)
        assertEquals(rig.engine.requests[0].clientReqId, rig.engine.requests[1].clientReqId)
        assertEquals(2, rig.named(EventCatalog.AI_REQUEST).size)
    }

    @Test fun `Stop keeps the partial text in Ready and writes nothing`() = runTest {
        val rig = AiRig(this)
        rig.start()
        rig.delta("Hello wo")
        rig.controller.stop()
        runCurrent()
        assertEquals(AiSheetState.Ready(RewriteMode.FIX, "Hello wo", trimmed = false), rig.state)
        rig.delta("rld")
        rig.done()
        assertEquals(AiSheetState.Ready(RewriteMode.FIX, "Hello wo", trimmed = false), rig.state)
        assertEquals("helo wrld", rig.editor.text)
        assertEquals(0, rig.lru.size)
    }

    @Test fun `a trimmed Done is marked trimmed`() = runTest {
        val rig = AiRig(this)
        rig.start(RewriteMode.LONGER)
        rig.delta("Long")
        rig.done(trimmed = true)
        assertEquals(AiSheetState.Ready(RewriteMode.LONGER, "Long", trimmed = true), rig.state)
    }

    @Test fun `errors map to their analytics reason and retry flag`() = runTest {
        val cases = listOf(
            AiError.Offline to ("offline" to true),
            AiError.Timeout to ("timeout" to true),
            AiError.RateLimited(7) to ("rate_limited" to true),
            AiError.Refused to ("refused" to false),
            AiError.TooLong to ("too_long" to false),
            AiError.Unavailable to ("unavailable" to true),
            AiError.NotConfigured to ("unavailable" to false),
        )
        for ((error, expected) in cases) {
            val rig = AiRig(this)
            rig.start()
            rig.fail(error)
            assertEquals(AiSheetState.Error(error, canRetry = expected.second), rig.state, "$error")
            assertEquals(expected.first, rig.named(EventCatalog.AI_ERROR).single().params["reason"], "$error")
        }
    }

    @Test fun `a selection over 2,000 chars is too long and nothing is sent`() = runTest {
        val rig = AiRig(this, host = "a".repeat(2_500))
        rig.editor.hostMoveCursor(0, 2_500)
        rig.start()
        assertEquals(AiSheetState.Error(AiError.TooLong, canRetry = false), rig.state)
        assertTrue(rig.engine.requests.isEmpty())
    }

    @Test fun `an empty field has nothing to rewrite`() = runTest {
        val rig = AiRig(this, host = "   ")
        rig.start()
        assertEquals(AiSheetState.NothingToRewrite, rig.state)
        assertTrue(rig.engine.requests.isEmpty())
    }

    @Test fun `Copy hands the output to the clipboard and writes nothing`() = runTest {
        val rig = AiRig(this)
        rig.rewrite("Hello world")
        rig.controller.copy()
        assertEquals(listOf("Hello world"), rig.clipboard)
        assertEquals("helo wrld", rig.editor.text)
        assertTrue(rig.named(EventCatalog.AI_REWRITE_DONE).isEmpty())
    }

    @Test fun `submitReport without the text sends a null text`() = runTest {
        val rig = AiRig(this)
        rig.rewrite("Hello world")
        rig.controller.openReport()
        assertIs<AiSheetState.Reporting>(rig.state)
        rig.controller.submitReport(ReportReason.WRONG, includeText = false)
        runCurrent()
        val payload = rig.reports.single()
        assertNull(payload.text)
        assertEquals(ReportReason.WRONG, payload.reason)
        assertEquals(AiActionController.PROMPT_REWRITE, payload.promptId)
        assertEquals(rig.engine.requests.single().clientReqId, payload.clientReqId)
        assertEquals("fix", rig.named(EventCatalog.AI_OUTPUT_REPORTED).single().params["tone"])
        assertEquals(AiSheetState.Ready(RewriteMode.FIX, "Hello world", trimmed = false), rig.state)
    }

    @Test fun `submitReport with the text attaches the output`() = runTest {
        val rig = AiRig(this)
        rig.rewrite("Hello world")
        rig.controller.openReport()
        rig.controller.submitReport(ReportReason.OFFENSIVE, includeText = true)
        runCurrent()
        assertEquals("Hello world", rig.reports.single().text)
    }

    @Test fun `ai_request engine cloud is logged once per network call`() = runTest {
        val rig = AiRig(this)
        rig.rewrite("A")
        rig.controller.chooseTone(RewriteMode.FORMAL)
        runCurrent()
        rig.delta("B")
        rig.done()
        rig.controller.chooseTone(RewriteMode.FIX)
        runCurrent()
        val requests = rig.named(EventCatalog.AI_REQUEST)
        assertEquals(2, requests.size)
        assertTrue(requests.all { it.params == mapOf("engine" to "cloud") })
        assertEquals(2, rig.engine.requests.size)
    }

    @Test fun `no analytics param holds the field text or the output`() = runTest {
        val rig = AiRig(this)
        rig.rewrite("Hello world")
        rig.controller.replace()
        rig.controller.undo()
        rig.start()
        rig.fail(AiError.Refused)
        for (event in rig.events) {
            for (value in event.params.values) {
                assertFalse(value.toString().contains("helo"), event.name)
                assertFalse(value.toString().contains("Hello"), event.name)
            }
        }
    }

    @Test fun `state toString never holds text`() {
        val states = listOf(
            AiSheetState.Streaming(RewriteMode.FIX, "secret words", stillWorking = false),
            AiSheetState.Ready(RewriteMode.FIX, "secret words", trimmed = false),
            AiSheetState.TextChanged("secret words"),
        )
        for (s in states) assertFalse(s.toString().contains("secret"))
        assertFalse(ReportPayload("id", "rewrite@1", ReportReason.OTHER, "secret words").toString().contains("secret"))
    }

    @Test fun `a write is refused once the decision forbids AI`() = runTest {
        val rig = AiRig(this)
        rig.rewrite("Hello world")
        rig.decision = AiRig.PASSWORD
        rig.controller.insert()
        rig.controller.replace()
        rig.controller.copy()
        assertEquals("helo wrld", rig.editor.text)
        assertTrue(rig.clipboard.isEmpty())
        assertEquals(AiSheetState.Hidden, rig.state)
    }
}
