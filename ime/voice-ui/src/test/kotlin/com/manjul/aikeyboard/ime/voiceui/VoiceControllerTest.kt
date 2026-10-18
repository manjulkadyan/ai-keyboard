/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.voiceui

import android.media.AudioManager
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.voice.EngineKind
import com.manjul.aikeyboard.core.voice.SpeechEngine
import com.manjul.aikeyboard.core.voice.SpeechSession
import com.manjul.aikeyboard.core.voice.SpokenPunctuation
import com.manjul.aikeyboard.core.voice.VoiceTimings
import com.manjul.aikeyboard.core.voice.platform.AudioFocusHolder
import com.manjul.aikeyboard.core.voice.platform.RecognizerCapabilities
import com.manjul.aikeyboard.core.voice.platform.SpeechEngineSelector
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceControllerTest {
    private class Engine(override val kind: EngineKind) : SpeechEngine {
        override val supportsPartials = true
        var listener: SpeechEngine.Listener? = null
        var destroyed = false
        override fun start(lang: String, listener: SpeechEngine.Listener) { this.listener = listener }
        override fun stop() {}
        override fun cancel() {}
        override fun destroy() { destroyed = true }
    }

    private class Caps(val service: Boolean = true, val onDevice: Boolean = true) : RecognizerCapabilities {
        override val sdkInt = 34
        override fun isRecognitionAvailable() = service
        override fun isOnDeviceAvailable() = onDevice
        override suspend fun onDeviceSupports(lang: String): Boolean? = true
        override fun whisperModelReady(lang: String) = false
        override fun whisperLangEnabled(lang: String) = false
    }

    private class Field : TextTarget {
        val calls = mutableListOf<String>()
        override fun setComposing(text: String): Boolean { calls += "set:$text"; return true }
        override fun commit(text: String, removeSpaceBefore: Boolean): Boolean { calls += "commit:$text"; return true }
        override fun finishComposing(): Boolean { calls += "finish"; return true }
    }

    private class Flag : FirstDictationFlag {
        override var logged = false
        override fun markLogged() { logged = true }
    }

    private class Rig(val scope: TestScope, caps: Caps = Caps()) {
        var permission = true
        var permissionChecks = 0
        var decision = PolicyDecision.Unrestricted
        var generation = 1
        var sessionsCreated = 0
        var launches = 0
        val engines = mutableListOf<Engine>()
        val events = mutableListOf<AnalyticsEvent>()
        val field = Field()
        val flag = Flag()
        val audioManager: AudioManager = RuntimeEnvironment.getApplication().getSystemService(AudioManager::class.java)
        val focus = AudioFocusHolder(audioManager)
        // Collectors and session timers outlive a test body; the background scope cancels them at the end.
        val controller = VoiceController(
            scope = scope.backgroundScope,
            sessionFactory = { factory ->
                sessionsCreated++
                SpeechSession(factory, scope.backgroundScope, VoiceTimings(), SpokenPunctuation, clock = { 0L })
            },
            engines = object : VoiceEngines {
                override fun supports(kind: EngineKind) = kind != EngineKind.WHISPER
                override fun create(kind: EngineKind, segmented: Boolean) = Engine(kind).also { engines += it }
            },
            selector = SpeechEngineSelector(caps),
            request = { VoiceRequest("en-US", preferWhisper = false, online = true) },
            permission = { permissionChecks++; permission },
            decision = { decision },
            generation = { generation },
            focus = focus,
            span = ComposingSpanController(field),
            analytics = { events += it },
            firstDictation = flag,
            launchPermission = { launches++ },
            clock = { 0L },
        )
        val engine get() = engines.last()

        fun names() = events.map { it.name }
    }

    private fun run(block: suspend TestScope.() -> Unit) = runTest(StandardTestDispatcher()) { block() }

    @Test fun missingPermissionShowsTheChipAndCreatesNoSession() = run {
        val r = Rig(this)
        r.permission = false
        r.controller.toggle()
        runCurrent()
        assertEquals(VoiceUiState.NeedsPermission, r.controller.ui.value)
        assertEquals(0, r.sessionsCreated)
        assertEquals(0, r.launches)
    }

    @Test fun chipTapLaunchesThePermissionScreenOnce() = run {
        val r = Rig(this)
        r.permission = false
        r.controller.toggle()
        r.controller.requestPermission()
        r.controller.requestPermission()
        assertEquals(1, r.launches)
        assertEquals(VoiceUiState.Hidden, r.controller.ui.value)
    }

    @Test fun permissionIsCheckedOnEveryToggle() = run {
        val r = Rig(this)
        r.permission = false
        r.controller.toggle()
        r.controller.dismiss()
        r.permission = true
        r.controller.toggle()
        runCurrent()
        assertEquals(2, r.permissionChecks)
        assertEquals(1, r.sessionsCreated)
        assertTrue(r.controller.ui.value is VoiceUiState.Listening)
    }

    @Test fun policyWithoutVoiceMakesToggleANoOp() = run {
        val r = Rig(this)
        r.decision = PolicyDecision.Locked
        r.controller.toggle()
        runCurrent()
        assertEquals(VoiceUiState.Hidden, r.controller.ui.value)
        assertEquals(0, r.sessionsCreated)
        assertTrue(r.events.isEmpty())
    }

    @Test fun startAcquiresFocusAndLogsSessionStart() = run {
        val r = Rig(this)
        r.controller.toggle()
        runCurrent()
        assertTrue(r.focus.isHeld)
        assertEquals(listOf("voice_session_start"), r.names())
        assertEquals("system_on_device", r.events.single().params["engine"])
    }

    @Test fun cancelReleasesFocusAndLogsFieldChange() = run {
        val r = Rig(this)
        r.controller.toggle()
        runCurrent()
        r.engine.listener!!.onReady()
        r.engine.listener!!.onPartial("hel")
        runCurrent()
        r.controller.cancel("field_change")
        runCurrent()
        assertFalse(r.focus.isHeld)
        assertTrue(r.engine.destroyed)
        val end = r.events.single { it.name == "voice_session_end" }
        assertEquals("field_change", end.params["end_reason"])
        assertEquals(VoiceUiState.Hidden, r.controller.ui.value)
        assertEquals(listOf("set:hel", "finish"), r.field.calls)
    }

    @Test fun lateCommitFromAnOldGenerationIsDiscarded() = run {
        val r = Rig(this)
        r.controller.toggle()
        runCurrent()
        r.engine.listener!!.onReady()
        // The field changes, then the engine delivers a final result for the old one.
        r.generation = 2
        r.engine.listener!!.onFinal("secret words")
        runCurrent()
        assertTrue(r.field.calls.none { it.startsWith("commit:") })
        assertFalse(r.focus.isHeld)
        assertEquals(VoiceUiState.Hidden, r.controller.ui.value)
        assertEquals("field_change", r.events.single { it.name == "voice_session_end" }.params["end_reason"])
    }

    @Test fun unavailableEngineShowsAMessageWithTheOffers() = run {
        val r = Rig(this, Caps(service = true, onDevice = false))
        val offline = VoiceController(
            scope = backgroundScope,
            sessionFactory = { error("no session expected") },
            engines = object : VoiceEngines {
                override fun supports(kind: EngineKind) = true
                override fun create(kind: EngineKind, segmented: Boolean): SpeechEngine = error("no engine expected")
            },
            selector = SpeechEngineSelector(Caps(service = true, onDevice = false)),
            request = { VoiceRequest("en-US", preferWhisper = false, online = false) },
            permission = { true },
            decision = { PolicyDecision.Unrestricted },
            generation = { 1 },
            focus = r.focus,
            span = ComposingSpanController(r.field),
            analytics = { r.events += it },
            firstDictation = r.flag,
            launchPermission = {},
            clock = { 0L },
        )
        offline.toggle()
        runCurrent()
        assertEquals(VoiceUiState.Message(MessageKind.OFFLINE_NO_MODEL, offerSystemDownload = true), offline.ui.value)
        assertEquals("offline_no_model", r.events.single { it.name == "voice_unavailable" }.params["reason"])
        assertFalse(r.focus.isHeld)
    }

    @Test fun micBusyShowsAMessage() = run {
        val r = Rig(this)
        shadowOf(r.audioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        r.controller.toggle()
        runCurrent()
        assertEquals(VoiceUiState.Message(MessageKind.MIC_BUSY), r.controller.ui.value)
        assertEquals(0, r.sessionsCreated)
    }

    @Test fun firstDictationIsLoggedOnceForASessionWithWords() = run {
        val r = Rig(this)
        repeat(2) {
            r.controller.toggle()
            runCurrent()
            r.engine.listener!!.onReady()
            r.engine.listener!!.onPartial("hello world")
            r.controller.toggle()
            r.engine.listener!!.onFinal("hello world")
            runCurrent()
        }
        assertEquals(1, r.events.count { it.name == "first_dictation" })
        assertEquals("en-US", r.events.single { it.name == "first_dictation" }.params["lang"])
        assertTrue(r.flag.logged)
        assertEquals(2, r.events.count { it.name == "voice_session_end" })
        assertEquals(listOf("set:hello world", "commit:hello world ", "set:hello world", "commit:hello world "), r.field.calls)
    }

    @Test fun firstDictationIsNotLoggedWhenThePersistedFlagIsSet() = run {
        val r = Rig(this)
        r.flag.logged = true
        r.controller.toggle()
        runCurrent()
        r.engine.listener!!.onFinal("hi")
        runCurrent()
        assertTrue(r.events.none { it.name == "first_dictation" })
    }

    @Test fun mutedSessionWithoutWordsDoesNotLogFirstDictation() = run {
        val r = Rig(this)
        r.controller.toggle()
        runCurrent()
        r.controller.toggle()
        runCurrent()
        r.engine.listener!!.onFinal("")
        runCurrent()
        assertTrue(r.events.none { it.name == "first_dictation" })
        assertFalse(r.focus.isHeld)
    }

    @Test fun toggleWhileListeningStopsAndCommits() = run {
        val r = Rig(this)
        r.controller.toggle()
        runCurrent()
        r.engine.listener!!.onReady()
        r.engine.listener!!.onPartial("hi there")
        runCurrent()
        r.controller.toggle()
        runCurrent()
        assertEquals(VoiceUiState.Transcribing, r.controller.ui.value)
        r.engine.listener!!.onFinal("hi there")
        runCurrent()
        assertEquals(VoiceUiState.Hidden, r.controller.ui.value)
        assertEquals("commit:hi there ", r.field.calls.last())
        assertEquals("user", r.events.single { it.name == "voice_session_end" }.params["end_reason"])
    }
}
