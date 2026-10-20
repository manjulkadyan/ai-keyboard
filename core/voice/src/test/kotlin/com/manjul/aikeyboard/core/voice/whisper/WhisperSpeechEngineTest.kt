/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.voice.whisper

import com.manjul.aikeyboard.core.voice.EngineKind
import com.manjul.aikeyboard.core.voice.SpeechEngine
import com.manjul.aikeyboard.lib.whisper.WhisperApi
import com.manjul.aikeyboard.lib.whisper.WhisperException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WhisperSpeechEngineTest {
    private class FakeApi : WhisperApi {
        var loads = 0
        val frees = mutableListOf<Long>()
        var transcribes = 0
        var lastPcm: FloatArray? = null
        var lastLang: String? = null
        var text = "hello world"
        var failWith: RuntimeException? = null
        var onTranscribe: () -> Unit = {}

        override fun load(modelPath: String, threads: Int): Long {
            loads++
            return 7L
        }

        override fun transcribe(ctx: Long, pcm16kMono: FloatArray, lang: String, translate: Boolean): String {
            transcribes++
            lastPcm = pcm16kMono
            lastLang = lang
            onTranscribe()
            failWith?.let { throw it }
            return text
        }

        override fun free(ctx: Long) {
            frees += ctx
        }
    }

    private class FakeRecorder : PcmRecorder {
        var sink: ((FloatArray, Int) -> Unit)? = null
        var failure: (() -> Unit)? = null
        var starts = 0
        var stops = 0

        override fun start(onSamples: (FloatArray, Int) -> Unit, onFailure: () -> Unit): Boolean {
            starts++
            sink = onSamples
            failure = onFailure
            return true
        }

        override fun stop() {
            stops++
            sink = null
            failure = null
        }

        /** The microphone went away mid-recording (a negative read), as the recorder thread reports it. */
        fun lose() {
            sink = null
            failure?.invoke()
        }

        /** Feeds [seconds] of a constant-amplitude signal in 100 ms chunks, as the recorder thread would. */
        fun feed(seconds: Int, amplitude: Float = 0.3f) {
            val chunk = FloatArray(1600) { if (it % 2 == 0) amplitude else -amplitude }
            repeat(seconds * 10) { sink?.invoke(chunk, chunk.size) }
        }
    }

    private class Events : SpeechEngine.Listener {
        val all = mutableListOf<String>()
        val rms = mutableListOf<Float>()
        override fun onReady() { all += "ready" }
        override fun onRms(db: Float) { rms += db }
        override fun onPartial(text: String) { all += "partial:$text" }
        override fun onSegment(text: String) { all += "segment:$text" }
        override fun onFinal(text: String) { all += "final:$text" }
        override fun onEndOfSpeech() { all += "end" }
        override fun onError(code: Int) { all += "error:$code" }
    }

    private class Rig(scope: TestScope, modelPath: String? = "/models/ggml-base-q5_1.bin") {
        val api = FakeApi()
        val recorder = FakeRecorder()
        val contexts = WhisperContexts(api, threads = 2)
        var idleFrees = 0
        val idle =
            WhisperIdleUnloader(clock = { scope.currentTime }, scope = scope.backgroundScope, free = { idleFrees++ })
        val events = Events()
        val engine = WhisperSpeechEngine(
            contexts = contexts,
            models = WhisperModelSource { modelPath },
            recorder = recorder,
            scope = scope,
            idle = idle,
            transcribeDispatcher = StandardTestDispatcher(scope.testScheduler),
        )
    }

    @Test fun recordsEmitsRmsAndDeliversTheFinalTranscriptAfterStop() = runTest {
        val r = Rig(this)
        assertEquals(EngineKind.WHISPER, r.engine.kind)
        assertFalse(r.engine.supportsPartials)
        r.engine.start("en-US", r.events)
        runCurrent()
        assertEquals(listOf("ready"), r.events.all)
        r.recorder.feed(seconds = 1)
        runCurrent()
        assertTrue(r.events.rms.isNotEmpty())
        assertTrue(r.events.rms.all { it > 3f }, "speech-level audio reads as speech: ${r.events.rms}")
        r.engine.stop()
        advanceUntilIdle()
        assertEquals(listOf("ready", "end", "final:hello world"), r.events.all)
        assertEquals(1, r.recorder.stops)
        assertEquals("en", r.api.lastLang)
        assertEquals(16_000, r.api.lastPcm!!.size)
    }

    @Test fun silenceReadsBelowTheSpeechThreshold() = runTest {
        val r = Rig(this)
        r.engine.start("en", r.events)
        runCurrent()
        r.recorder.feed(seconds = 1, amplitude = 0.0005f)
        runCurrent()
        assertTrue(r.events.rms.all { it < 3f }, "${r.events.rms}")
    }

    @Test fun sixtySecondCapStopsRecordingAndTranscribes() = runTest {
        val r = Rig(this)
        r.engine.start("en", r.events)
        runCurrent()
        r.recorder.feed(seconds = 61)
        advanceUntilIdle()
        assertEquals(1, r.recorder.stops)
        assertEquals(60 * 16_000, r.api.lastPcm!!.size)
        assertEquals(listOf("ready", "end", "final:hello world"), r.events.all)
        // The session's own stop after the cap is a no-op.
        r.engine.stop()
        advanceUntilIdle()
        assertEquals(1, r.api.transcribes)
        assertEquals(1, r.recorder.stops)
    }

    @Test fun noModelReportsLanguageUnavailableAndNeverOpensTheMic() = runTest {
        val r = Rig(this, modelPath = null)
        r.engine.start("en", r.events)
        advanceUntilIdle()
        assertEquals(listOf("error:13"), r.events.all)
        assertEquals(0, r.recorder.starts)
    }

    @Test fun pcmBufferIsClearedAfterTranscription() = runTest {
        val r = Rig(this)
        r.engine.start("en", r.events)
        runCurrent()
        r.recorder.feed(seconds = 2)
        r.engine.stop()
        advanceUntilIdle()
        val pcm = r.api.lastPcm!!
        assertTrue(pcm.isNotEmpty())
        assertTrue(pcm.all { it == 0f }, "audio must not outlive the transcription")
    }

    @Test fun whisperFailuresBecomeOnErrorNeverACrash() = runTest {
        for (failure in listOf(
            WhisperException("aborted"),
            IllegalStateException("busy"),
            IllegalArgumentException("stale handle"),
        )) {
            val r = Rig(this)
            r.api.failWith = failure
            r.engine.start("en", r.events)
            runCurrent()
            r.recorder.feed(seconds = 1)
            r.engine.stop()
            advanceUntilIdle()
            assertEquals("error:5", r.events.all.last(), failure.toString())
            assertTrue(r.events.all.none { it.startsWith("final") })
            assertTrue(r.api.lastPcm!!.all { it == 0f })
        }
    }

    @Test fun cancelDuringTranscribeFreesTheContextAndDropsTheLateResult() = runTest {
        val r = Rig(this)
        r.api.onTranscribe = { r.engine.cancel() }
        r.engine.start("en", r.events)
        runCurrent()
        r.recorder.feed(seconds = 1)
        r.engine.stop()
        advanceUntilIdle()
        assertEquals(listOf(7L), r.api.frees)
        assertEquals(listOf("ready", "end"), r.events.all)
    }

    @Test fun destroyDuringTranscribeAbortsAndSwallowsTheAbortError() = runTest {
        val r = Rig(this)
        r.api.onTranscribe = {
            r.engine.destroy()
            r.api.failWith = WhisperException("Transcription aborted: context freed")
        }
        r.engine.start("en", r.events)
        runCurrent()
        r.recorder.feed(seconds = 1)
        r.engine.stop()
        advanceUntilIdle()
        assertEquals(listOf(7L), r.api.frees)
        assertEquals(listOf("ready", "end"), r.events.all)
    }

    @Test fun cancelWhileRecordingStopsTheMicAndDropsTheAudio() = runTest {
        val r = Rig(this)
        r.engine.start("en", r.events)
        runCurrent()
        r.recorder.feed(seconds = 1)
        r.engine.cancel()
        r.engine.stop()
        advanceUntilIdle()
        assertEquals(1, r.recorder.stops)
        assertEquals(0, r.api.transcribes)
        assertEquals(listOf("ready"), r.events.all)
    }

    @Test fun theModelIsLoadedOnceAndSharedByLaterEngines() = runTest {
        val r = Rig(this)
        r.engine.start("en", r.events)
        runCurrent()
        r.recorder.feed(seconds = 1)
        r.engine.stop()
        advanceUntilIdle()
        val second = WhisperSpeechEngine(
            contexts = r.contexts,
            models = WhisperModelSource { "/models/ggml-base-q5_1.bin" },
            recorder = r.recorder,
            scope = this,
            idle = r.idle,
            transcribeDispatcher = StandardTestDispatcher(testScheduler),
        )
        val events = Events()
        second.start("en", events)
        runCurrent()
        r.recorder.feed(seconds = 1)
        second.stop()
        advanceUntilIdle()
        assertEquals(listOf("ready", "end", "final:hello world"), events.all)
        assertEquals(1, r.api.loads)
        assertEquals(2, r.api.transcribes)
    }

    @Test fun nonSpeechMarkersAreDropped() = runTest {
        val r = Rig(this)
        r.api.text = "[BLANK_AUDIO]"
        r.engine.start("en", r.events)
        runCurrent()
        r.recorder.feed(seconds = 1)
        r.engine.stop()
        advanceUntilIdle()
        assertEquals("final:", r.events.all.last())
    }

    @Test fun aLostMicrophoneIsAnAudioErrorAndDropsTheAudio() = runTest {
        val r = Rig(this)
        r.engine.start("en", r.events)
        runCurrent()
        r.recorder.feed(seconds = 1)
        r.recorder.lose()
        advanceUntilIdle()
        assertEquals(listOf("ready", "error:3"), r.events.all)
        assertEquals(0, r.api.transcribes)
        // A later stop from the session is a no-op.
        r.engine.stop()
        advanceUntilIdle()
        assertEquals(0, r.api.transcribes)
        assertEquals(listOf("ready", "error:3"), r.events.all)
    }

    @Test fun aFailureFromAnOldRecordingIsIgnored() = runTest {
        val r = Rig(this)
        r.engine.start("en", r.events)
        runCurrent()
        val old = r.recorder.failure!!
        r.engine.cancel()
        r.engine.start("en", r.events)
        runCurrent()
        old()
        advanceUntilIdle()
        assertEquals(listOf("ready", "ready"), r.events.all)
    }
}
