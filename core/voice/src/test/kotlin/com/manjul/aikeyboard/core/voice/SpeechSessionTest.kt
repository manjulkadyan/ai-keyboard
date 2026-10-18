/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.voice

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SpeechSessionTest {
    private class Rig(
        val scope: TestScope,
        timings: VoiceTimings = VoiceTimings(),
        private val make: () -> FakeSpeechEngine = { FakeSpeechEngine() },
    ) {
        val engines = mutableListOf<FakeSpeechEngine>()
        val session = SpeechSession(
            engineFactory = { make().also { engines += it } },
            scope = scope,
            timings = timings,
            punctuation = SpokenPunctuation,
            clock = { scope.currentTime },
        )
        val engine get() = engines.last()
        val l get() = engine.listener!!
    }

    private fun TestScope.rig(timings: VoiceTimings = VoiceTimings(), make: () -> FakeSpeechEngine = { FakeSpeechEngine() }) =
        Rig(this, timings, make)

    private fun runSession(block: suspend TestScope.() -> Unit) =
        runTest(StandardTestDispatcher()) { block() }

    @Test fun partialsEmitComposingAndFinalCommitsWithTrailingSpace() = runSession {
        val r = rig()
        r.session.output.test {
            r.session.start("en-US", continuous = false)
            runCurrent()
            r.l.onReady()
            r.l.onPartial("hel")
            r.l.onPartial("hello")
            assertEquals(VoiceOutput.Composing("hel"), awaitItem())
            assertEquals(VoiceOutput.Composing("hello"), awaitItem())
            r.l.onFinal("hello")
            assertEquals(VoiceOutput.Commit("hello "), awaitItem())
            assertEquals(VoiceState.Idle, r.session.state.value)
        }
    }

    @Test fun finalEndingInNewlineGetsNoTrailingSpace() = runSession {
        val r = rig()
        r.session.output.test {
            r.session.start("en", continuous = false)
            runCurrent()
            r.l.onFinal("hi new line")
            assertEquals(VoiceOutput.Commit("hi\n"), awaitItem())
        }
    }

    @Test fun listeningStateTracksReadyAndRms() = runSession {
        val r = rig()
        r.session.start("en", true)
        runCurrent()
        assertEquals(VoiceState.Starting, r.session.state.value)
        r.l.onReady()
        assertEquals(VoiceState.Listening(0f), r.session.state.value)
        r.l.onRms(-3f)
        assertEquals(VoiceState.Listening(-3f), r.session.state.value)
    }

    @Test fun continuousEndsAfterSilenceMsFollowingFinal() = runSession {
        val r = rig()
        r.session.start("en", true)
        runCurrent()
        r.l.onFinal("hello")
        advanceTimeBy(1999)
        assertNull(r.session.endReason)
        advanceTimeBy(2)
        assertEquals("silence", r.session.endReason)
        assertEquals(VoiceState.Idle, r.session.state.value)
    }

    @Test fun silenceMsIsConfigurable() = runSession {
        val r = rig(VoiceTimings(silenceMs = 5000))
        r.session.start("en", true)
        runCurrent()
        r.l.onFinal("hello")
        advanceTimeBy(4999)
        assertNull(r.session.endReason)
        advanceTimeBy(2)
        assertEquals("silence", r.session.endReason)
    }

    @Test fun speechAfterFinalCancelsSilenceTimer() = runSession {
        val r = rig()
        r.session.start("en", true)
        runCurrent()
        r.l.onFinal("hello")
        advanceTimeBy(300)
        assertEquals(2, r.engines.size)
        r.l.onPartial("more")
        advanceTimeBy(5000)
        assertNull(r.session.endReason)
    }

    @Test fun noSpeechErrorsRestartThreeTimesThenStopWithTapToResume() = runSession {
        val r = rig()
        r.session.start("en", true)
        runCurrent()
        for ((i, code) in listOf(6, 7, 6).withIndex()) {
            r.l.onError(code)
            assertTrue(r.engines[i].destroyed)
            advanceTimeBy(249)
            assertEquals(i + 1, r.engines.size)
            advanceTimeBy(2)
            assertEquals(i + 2, r.engines.size)
        }
        r.l.onError(7)
        assertEquals("error", r.session.endReason)
        assertEquals(VoiceState.Idle, r.session.state.value)
        assertTrue(r.session.tapToResume.value)
        advanceTimeBy(1000)
        assertEquals(4, r.engines.size)
    }

    @Test fun finalResetsRestartCounter() = runSession {
        val r = rig(VoiceTimings(silenceMs = 60_000, sessionCapMs = 600_000))
        r.session.start("en", true)
        runCurrent()
        repeat(2) { r.l.onError(6); advanceTimeBy(251) }
        r.l.onFinal("x")
        advanceTimeBy(251)
        repeat(3) { r.l.onError(6); advanceTimeBy(251) }
        assertNull(r.session.endReason)
        r.l.onError(6)
        assertEquals("error", r.session.endReason)
    }

    @Test fun busyDestroysAndRetriesThenFailsOnThird() = runSession {
        val r = rig()
        r.session.start("en", true)
        runCurrent()
        r.l.onError(8)
        assertTrue(r.engines[0].destroyed)
        advanceTimeBy(399)
        assertEquals(1, r.engines.size)
        advanceTimeBy(2)
        assertEquals(2, r.engines.size)
        r.l.onError(8)
        advanceTimeBy(401)
        assertEquals(3, r.engines.size)
        r.l.onError(8)
        assertEquals(VoiceState.Error(VoiceErrorKind.BUSY), r.session.state.value)
        advanceTimeBy(1000)
        assertEquals(3, r.engines.size)
    }

    @Test fun permissionErrorDoesNotRestart() = runSession {
        val r = rig()
        r.session.start("en", true)
        runCurrent()
        r.l.onError(9)
        assertEquals(VoiceState.Error(VoiceErrorKind.NO_PERMISSION), r.session.state.value)
        advanceTimeBy(5000)
        assertEquals(1, r.engines.size)
    }

    @Test fun networkAndLanguageErrorsMap() = runSession {
        for ((code, kind) in listOf(
            2 to VoiceErrorKind.NETWORK, 1 to VoiceErrorKind.NETWORK, 4 to VoiceErrorKind.NETWORK,
            12 to VoiceErrorKind.LANGUAGE_UNAVAILABLE, 13 to VoiceErrorKind.LANGUAGE_UNAVAILABLE,
            3 to VoiceErrorKind.OTHER,
        )) {
            val r = rig()
            r.session.start("en", true)
            runCurrent()
            r.l.onError(code)
            assertEquals(VoiceState.Error(kind), r.session.state.value, "code $code")
            assertEquals("error", r.session.endReason)
        }
    }

    @Test fun sessionCapCommitsPendingPartial() = runSession {
        val r = rig()
        r.session.output.test {
            r.session.start("en", true)
            runCurrent()
            r.l.onPartial("almost")
            assertEquals(VoiceOutput.Composing("almost"), awaitItem())
            advanceTimeBy(59_999)
            assertNull(r.session.endReason)
            advanceTimeBy(2)
            assertEquals(VoiceOutput.Commit("almost "), awaitItem())
            assertEquals("cap", r.session.endReason)
            assertEquals(VoiceState.Idle, r.session.state.value)
            assertTrue(r.engine.destroyed)
        }
    }

    @Test fun cancelClearsComposingWithoutCommit() = runSession {
        val r = rig()
        r.session.output.test {
            r.session.start("en", true)
            runCurrent()
            r.l.onPartial("draft")
            assertEquals(VoiceOutput.Composing("draft"), awaitItem())
            r.session.cancel()
            assertEquals(VoiceOutput.ClearComposing, awaitItem())
            assertTrue(r.engine.destroyed)
            assertEquals(VoiceState.Idle, r.session.state.value)
            assertEquals("field_change", r.session.endReason)
            advanceTimeBy(70_000)
            expectNoEvents()
        }
    }

    @Test fun staleEngineCallbacksAreIgnoredAfterCancel() = runSession {
        val r = rig()
        r.session.start("en", true)
        runCurrent()
        val old = r.l
        r.session.cancel()
        r.session.output.test {
            assertEquals(VoiceOutput.ClearComposing, awaitItem())
            old.onFinal("late")
            expectNoEvents()
        }
    }

    @Test fun userStopWaitsForFinalOnPartialEngine() = runSession {
        val r = rig()
        r.session.output.test {
            r.session.start("en", true)
            runCurrent()
            r.l.onPartial("bye")
            awaitItem()
            r.session.stopByUser()
            assertEquals(VoiceState.Finalizing, r.session.state.value)
            assertEquals(1, r.engine.stopCount)
            r.l.onFinal("bye")
            assertEquals(VoiceOutput.Commit("bye "), awaitItem())
            assertEquals("user", r.session.endReason)
            assertEquals(VoiceState.Idle, r.session.state.value)
        }
    }

    private fun whisper() = FakeSpeechEngine(EngineKind.WHISPER, supportsPartials = false)

    @Test fun whisperStopShowsTranscribingUntilFinal() = runSession {
        val r = rig { whisper() }
        r.session.output.test {
            r.session.start("en", false)
            runCurrent()
            r.l.onReady()
            r.session.stopByUser()
            assertEquals(VoiceState.Transcribing, r.session.state.value)
            advanceTimeBy(44_000)
            assertEquals(VoiceState.Transcribing, r.session.state.value)
            r.l.onFinal("transcribed")
            assertEquals(VoiceOutput.Commit("transcribed "), awaitItem())
            assertEquals(VoiceState.Idle, r.session.state.value)
            assertEquals("user", r.session.endReason)
        }
    }

    @Test fun transcribeTimeoutBoundaries() = runSession {
        val r = rig { whisper() }
        r.session.start("en", false)
        runCurrent()
        r.session.stopByUser()
        advanceTimeBy(44_999)
        assertEquals(VoiceState.Transcribing, r.session.state.value)
        advanceTimeBy(2)
        assertEquals(VoiceState.Error(VoiceErrorKind.OTHER), r.session.state.value)
        assertEquals("error", r.session.endReason)
        assertTrue(r.engine.destroyed)
        r.session.output.test { assertEquals(VoiceOutput.ClearComposing, awaitItem()); expectNoEvents() }
    }

    @Test fun capWithWhisperTranscribesInsteadOfDiscarding() = runSession {
        val r = rig { whisper() }
        r.session.output.test {
            r.session.start("en", false)
            runCurrent()
            advanceTimeBy(60_001)
            assertEquals(VoiceState.Transcribing, r.session.state.value)
            assertEquals(1, r.engine.stopCount)
            assertFalse(r.engine.destroyed)
            r.l.onFinal("long speech")
            assertEquals(VoiceOutput.Commit("long speech "), awaitItem())
            assertEquals("cap", r.session.endReason)
            assertEquals(VoiceState.Idle, r.session.state.value)
        }
    }

    @Test fun outputIsLosslessBeforeCollectorSubscribes() = runSession {
        val r = rig()
        r.session.start("en", false)
        runCurrent()
        r.l.onFinal("early")
        r.session.output.test { assertEquals(VoiceOutput.Commit("early "), awaitItem()) }
    }

    @Test fun fatalErrorWhileStoppingCommitsPendingPartial() = runSession {
        val r = rig()
        r.session.output.test {
            r.session.start("en", true)
            runCurrent()
            r.l.onPartial("keep")
            awaitItem()
            r.session.stopByUser()
            r.l.onError(2)
            assertEquals(VoiceOutput.Commit("keep "), awaitItem())
            assertEquals("user", r.session.endReason)
            assertEquals(VoiceState.Idle, r.session.state.value)
        }
    }

    @Test fun fatalErrorWhileStoppingWithoutTextJustFinishes() = runSession {
        val r = rig()
        r.session.start("en", true)
        runCurrent()
        r.session.stopByUser()
        r.l.onError(3)
        assertEquals("user", r.session.endReason)
        assertEquals(VoiceState.Idle, r.session.state.value)
        assertTrue(r.engine.destroyed)
    }

    @Test fun busyWhileStoppingDoesNotGetStuck() = runSession {
        val r = rig()
        r.session.start("en", true)
        runCurrent()
        r.session.stopByUser()
        r.l.onError(8)
        advanceTimeBy(2000)
        assertEquals("user", r.session.endReason)
        assertEquals(VoiceState.Idle, r.session.state.value)
        assertEquals(1, r.engines.size)
    }

    @Test fun whisperErrorWhileTranscribingSurfacesError() = runSession {
        val r = rig { whisper() }
        r.session.start("en", false)
        runCurrent()
        r.session.stopByUser()
        r.l.onError(2)
        assertEquals(VoiceState.Error(VoiceErrorKind.NETWORK), r.session.state.value)
        assertEquals("error", r.session.endReason)
    }

    @Test fun staleCallbackAfterRestartIsIgnored() = runSession {
        val r = rig()
        r.session.start("en", true)
        runCurrent()
        val old = r.l
        r.l.onError(6)
        advanceTimeBy(251)
        assertEquals(2, r.engines.size)
        r.session.output.test {
            old.onFinal("ghost")
            old.onPartial("ghost")
            expectNoEvents()
        }
        assertNull(r.session.endReason)
    }

    @Test fun cancelDuringRestartGapStopsRestart() = runSession {
        val r = rig()
        r.session.start("en", true)
        runCurrent()
        r.l.onError(6)
        r.session.cancel()
        advanceTimeBy(1000)
        assertEquals(1, r.engines.size)
        assertEquals("field_change", r.session.endReason)
        assertEquals(VoiceState.Idle, r.session.state.value)
    }

    @Test fun capHoldsAcrossRestarts() = runSession {
        val r = rig()
        r.session.start("en", true)
        runCurrent()
        advanceTimeBy(20_000); r.l.onError(6)
        advanceTimeBy(20_000); r.l.onError(7)
        advanceTimeBy(19_000)
        assertNull(r.session.endReason)
        advanceTimeBy(1_100)
        assertEquals("cap", r.session.endReason)
    }

    @Test fun segmentResetsRestartCounter() = runSession {
        val r = rig()
        r.session.start("en", true)
        runCurrent()
        repeat(3) { r.l.onError(6); advanceTimeBy(251) }
        r.l.onSegment("x")
        r.l.onError(6)
        assertNull(r.session.endReason)
    }

    @Test fun loudRmsRestartsSilenceWindow() = runSession {
        val r = rig()
        r.session.start("en", true)
        runCurrent()
        r.l.onFinal("hi")
        advanceTimeBy(300) // restarted engine
        advanceTimeBy(1500)
        r.l.onRms(5f)
        advanceTimeBy(1500)
        assertNull(r.session.endReason)
        advanceTimeBy(600)
        assertEquals("silence", r.session.endReason)
    }

    @Test fun segmentDetectionOnlyForContinuousPartialEngines() = runSession {
        val r1 = rig()
        r1.session.start("en", false)
        runCurrent()
        r1.l.onFinal("x")
        assertTrue(r1.session.isSegmentedSupported(EngineKind.SYSTEM_DEFAULT, "en"))
        val r2 = rig { whisper() }
        r2.session.start("en", true)
        runCurrent()
        r2.l.onFinal("x")
        assertTrue(r2.session.isSegmentedSupported(EngineKind.WHISPER, "en"))
    }

    @Test fun finalWithoutSegmentsMarksSegmentedUnsupported() = runSession {
        val r = rig()
        assertTrue(r.session.isSegmentedSupported(EngineKind.SYSTEM_DEFAULT, "en"))
        r.session.start("en", true)
        runCurrent()
        r.l.onFinal("hello")
        assertFalse(r.session.isSegmentedSupported(EngineKind.SYSTEM_DEFAULT, "en"))
        assertTrue(r.session.isSegmentedSupported(EngineKind.SYSTEM_DEFAULT, "ur"))
        assertTrue(r.session.isSegmentedSupported(EngineKind.WHISPER, "en"))
    }

    @Test fun segmentsCommitAndKeepSegmentedSupported() = runSession {
        val r = rig()
        r.session.output.test {
            r.session.start("en", true)
            runCurrent()
            r.l.onSegment("one")
            assertEquals(VoiceOutput.Commit("one "), awaitItem())
            r.l.onFinal("two")
            assertEquals(VoiceOutput.Commit("two "), awaitItem())
        }
        assertTrue(r.session.isSegmentedSupported(EngineKind.SYSTEM_DEFAULT, "en"))
    }

    @Test fun spokenPunctuationAppliesToCommit() = runSession {
        val r = rig()
        r.session.output.test {
            r.session.start("en", false)
            runCurrent()
            r.l.onFinal("hello comma world")
            assertEquals(VoiceOutput.Commit("hello, world "), awaitItem())
        }
    }
}
