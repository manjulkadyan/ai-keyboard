/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.voice.platform

import com.manjul.aikeyboard.core.datastore.VoiceEnginePreference
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

    private class Case(
        val name: String,
        val caps: Caps,
        val preference: VoiceEnginePreference,
        val online: Boolean,
        val expected: EngineChoice,
    )

    private fun use(kind: EngineKind) = EngineChoice.Use(kind)

    private fun unavailable(
        reason: String,
        offerSystemDownload: Boolean,
        offerWhisperDownload: Boolean,
        localOnly: Boolean = false,
    ) = EngineChoice.Unavailable(reason, offerSystemDownload, offerWhisperDownload, localOnly)

    private suspend fun check(cases: List<Case>) {
        for (c in cases) {
            assertEquals(c.name, c.expected, SpeechEngineSelector(c.caps).choose("en-US", c.preference, c.online))
        }
    }

    @Test
    fun auto() = runTest {
        val auto = VoiceEnginePreference.AUTO
        check(
            listOf(
                Case("on-device supports", Caps(), auto, false, use(EngineKind.SYSTEM_ON_DEVICE)),
                Case("on-device support unknown", Caps(supports = null), auto, false, use(EngineKind.SYSTEM_ON_DEVICE)),
                Case("model ready is not preferred", Caps(modelReady = true, whisperEnabled = true), auto, true,
                    use(EngineKind.SYSTEM_ON_DEVICE)),
                Case("on-device lacks lang, online", Caps(supports = false), auto, true,
                    use(EngineKind.SYSTEM_DEFAULT)),
                Case("sdk 30 uses default when online", Caps(sdkInt = 30), auto, true, use(EngineKind.SYSTEM_DEFAULT)),
                Case("lacks lang offline, whisper ready",
                    Caps(supports = false, modelReady = true, whisperEnabled = true), auto, false,
                    use(EngineKind.WHISPER)),
                Case("no service, whisper ready", Caps(service = false, onDevice = false, modelReady = true,
                    whisperEnabled = true), auto, true, use(EngineKind.WHISPER)),
                Case(
                    "no service, no model, whisper enabled",
                    Caps(service = false, onDevice = false, whisperEnabled = true), auto, true,
                    unavailable("no_service", offerSystemDownload = false, offerWhisperDownload = true),
                ),
                Case(
                    "no service, no model, whisper disabled",
                    Caps(service = false, onDevice = false), auto, true,
                    unavailable("no_service", offerSystemDownload = false, offerWhisperDownload = false),
                ),
                Case(
                    "offline, no on-device, no model, sdk 34",
                    Caps(onDevice = false, whisperEnabled = true), auto, false,
                    unavailable("offline_no_model", offerSystemDownload = true, offerWhisperDownload = true),
                ),
                Case(
                    "offline, no on-device, no model, sdk 30",
                    Caps(sdkInt = 30, onDevice = false), auto, false,
                    unavailable("offline_no_model", offerSystemDownload = false, offerWhisperDownload = false),
                ),
                Case(
                    "sdk 30 offline with onDevice flag set",
                    Caps(sdkInt = 30), auto, false,
                    unavailable("offline_no_model", offerSystemDownload = false, offerWhisperDownload = false),
                ),
                Case(
                    "whisper ready but not enabled, offline",
                    Caps(onDevice = false, modelReady = true), auto, false,
                    unavailable("offline_no_model", offerSystemDownload = true, offerWhisperDownload = false),
                ),
                Case(
                    "offline, on-device lacks lang",
                    Caps(supports = false), auto, false,
                    unavailable("lang_unsupported", offerSystemDownload = true, offerWhisperDownload = false),
                ),
            ),
        )
    }

    @Test
    fun onDeviceNeverUsesTheServerRecognizer() = runTest {
        val onDevice = VoiceEnginePreference.ON_DEVICE
        check(
            listOf(
                Case("on-device supports", Caps(), onDevice, true, use(EngineKind.SYSTEM_ON_DEVICE)),
                Case("on-device support unknown", Caps(supports = null), onDevice, true,
                    use(EngineKind.SYSTEM_ON_DEVICE)),
                Case("on-device first even with a model", Caps(modelReady = true, whisperEnabled = true), onDevice,
                    true, use(EngineKind.SYSTEM_ON_DEVICE)),
                Case("no on-device, whisper ready", Caps(onDevice = false, modelReady = true, whisperEnabled = true),
                    onDevice, true, use(EngineKind.WHISPER)),
                Case("lacks lang, whisper ready", Caps(supports = false, modelReady = true, whisperEnabled = true),
                    onDevice, true, use(EngineKind.WHISPER)),
                Case(
                    "online, service available, no on-device: not the server",
                    Caps(onDevice = false), onDevice, true,
                    unavailable(
                        "offline_no_model", offerSystemDownload = false, offerWhisperDownload = false, localOnly = true,
                    ),
                ),
                Case(
                    "sdk 30, online: not the server",
                    Caps(sdkInt = 30), onDevice, true,
                    unavailable(
                        "offline_no_model", offerSystemDownload = false, offerWhisperDownload = false, localOnly = true,
                    ),
                ),
                Case(
                    "online, on-device lacks lang: not the server",
                    Caps(supports = false), onDevice, true,
                    unavailable(
                        "lang_unsupported", offerSystemDownload = true, offerWhisperDownload = false, localOnly = true,
                    ),
                ),
                Case(
                    "model ready but language not enabled",
                    Caps(onDevice = false, modelReady = true), onDevice, true,
                    unavailable(
                        "offline_no_model", offerSystemDownload = false, offerWhisperDownload = false, localOnly = true,
                    ),
                ),
                Case(
                    "no on-device, whisper enabled, no model",
                    Caps(onDevice = false, whisperEnabled = true), onDevice, false,
                    unavailable(
                        "offline_no_model", offerSystemDownload = false, offerWhisperDownload = true, localOnly = true,
                    ),
                ),
                Case(
                    "no service at all",
                    Caps(service = false, onDevice = false), onDevice, true,
                    unavailable(
                        "no_service", offerSystemDownload = false, offerWhisperDownload = false, localOnly = true,
                    ),
                ),
            ),
        )
    }

    @Test
    fun whisperFallsBackToOnDeviceOnly() = runTest {
        val whisper = VoiceEnginePreference.WHISPER
        check(
            listOf(
                Case("model ready", Caps(modelReady = true, whisperEnabled = true), whisper, true,
                    use(EngineKind.WHISPER)),
                Case("model ready, lang not enabled", Caps(modelReady = true), whisper, true, use(EngineKind.WHISPER)),
                Case("no model falls to on-device", Caps(whisperEnabled = true), whisper, true,
                    use(EngineKind.SYSTEM_ON_DEVICE)),
                Case(
                    "online, service available, no on-device, no model: not the server",
                    Caps(onDevice = false, whisperEnabled = true), whisper, true,
                    unavailable(
                        "offline_no_model", offerSystemDownload = false, offerWhisperDownload = true, localOnly = true,
                    ),
                ),
                Case(
                    "online, on-device lacks lang, no model: not the server",
                    Caps(supports = false, whisperEnabled = true), whisper, true,
                    unavailable(
                        "lang_unsupported", offerSystemDownload = true, offerWhisperDownload = true, localOnly = true,
                    ),
                ),
                Case(
                    "no service, no model",
                    Caps(service = false, onDevice = false, whisperEnabled = true), whisper, true,
                    unavailable(
                        "no_service", offerSystemDownload = false, offerWhisperDownload = true, localOnly = true,
                    ),
                ),
            ),
        )
    }
}
