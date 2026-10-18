/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.voice.platform

import com.manjul.aikeyboard.core.voice.EngineKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechEngineSelectorTest {
    private class Caps(
        override val sdkInt: Int = 34,
        val service: Boolean = true,
        val onDevice: Boolean = true,
        val supports: Boolean? = true,
        val modelReady: Boolean = false,
        val whisperEnabled: Boolean = false,
    ) : RecognizerCapabilities {
        override fun isRecognitionAvailable() = service
        override fun isOnDeviceAvailable() = onDevice
        override suspend fun onDeviceSupports(lang: String) = supports
        override fun whisperModelReady(lang: String) = modelReady
        override fun whisperLangEnabled(lang: String) = whisperEnabled
    }

    private class Case(val name: String, val caps: Caps, val preferWhisper: Boolean, val online: Boolean, val expected: EngineChoice)

    private fun use(kind: EngineKind) = EngineChoice.Use(kind)

    @Test
    fun table() = runTest {
        val cases = listOf(
            Case("prefer whisper, ready", Caps(modelReady = true), true, true, use(EngineKind.WHISPER)),
            Case("prefer whisper, model missing falls to on-device", Caps(), true, true, use(EngineKind.SYSTEM_ON_DEVICE)),
            Case("on-device supports", Caps(), false, false, use(EngineKind.SYSTEM_ON_DEVICE)),
            Case("on-device support unknown", Caps(supports = null), false, false, use(EngineKind.SYSTEM_ON_DEVICE)),
            Case("on-device lacks lang, online", Caps(supports = false), false, true, use(EngineKind.SYSTEM_DEFAULT)),
            Case("sdk 30 uses default when online", Caps(sdkInt = 30, onDevice = true), false, true, use(EngineKind.SYSTEM_DEFAULT)),
            Case("lacks lang offline, whisper ready", Caps(supports = false, modelReady = true, whisperEnabled = true), false, false, use(EngineKind.WHISPER)),
            Case("no service, whisper ready", Caps(service = false, onDevice = false, modelReady = true, whisperEnabled = true), false, true, use(EngineKind.WHISPER)),
            Case(
                "no service, no model, whisper enabled",
                Caps(service = false, onDevice = false, whisperEnabled = true), false, true,
                EngineChoice.Unavailable("no_service", offerSystemDownload = false, offerWhisperDownload = true),
            ),
            Case(
                "no service, no model, whisper disabled",
                Caps(service = false, onDevice = false), false, true,
                EngineChoice.Unavailable("no_service", offerSystemDownload = false, offerWhisperDownload = false),
            ),
            Case(
                "offline, no on-device, no model, sdk 34",
                Caps(onDevice = false, whisperEnabled = true), false, false,
                EngineChoice.Unavailable("offline_no_model", offerSystemDownload = true, offerWhisperDownload = true),
            ),
            Case(
                "offline, no on-device, no model, sdk 30",
                Caps(sdkInt = 30, onDevice = false), false, false,
                EngineChoice.Unavailable("offline_no_model", offerSystemDownload = false, offerWhisperDownload = false),
            ),
            Case(
                "sdk 30 offline with onDevice flag set",
                Caps(sdkInt = 30, onDevice = true), false, false,
                EngineChoice.Unavailable("offline_no_model", offerSystemDownload = false, offerWhisperDownload = false),
            ),
            Case(
                "whisper ready but not enabled, offline",
                Caps(onDevice = false, modelReady = true, whisperEnabled = false), false, false,
                EngineChoice.Unavailable("offline_no_model", offerSystemDownload = true, offerWhisperDownload = false),
            ),
            Case("preferWhisper, model ready, lang not enabled", Caps(modelReady = true, whisperEnabled = false), true, true, use(EngineKind.WHISPER)),
            Case(
                "offline, on-device lacks lang",
                Caps(supports = false), false, false,
                EngineChoice.Unavailable("lang_unsupported", offerSystemDownload = true, offerWhisperDownload = false),
            ),
        )
        for (c in cases) {
            assertEquals(c.name, c.expected, SpeechEngineSelector(c.caps).choose("en-US", c.preferWhisper, c.online))
        }
    }
}
