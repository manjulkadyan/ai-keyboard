/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.voice.platform

import android.os.Build
import com.manjul.aikeyboard.core.datastore.VoiceEnginePreference
import com.manjul.aikeyboard.core.voice.EngineKind

sealed interface EngineChoice {
    data class Use(val kind: EngineKind) : EngineChoice

    /**
     * [reason] is one of no_service, lang_unsupported, offline_no_model. [localOnly] is set when the engine
     * preference keeps audio on the phone, so the server recognizer was never an option.
     */
    data class Unavailable(
        val reason: String,
        val offerSystemDownload: Boolean,
        val offerWhisperDownload: Boolean,
        val localOnly: Boolean = false,
    ) : EngineChoice
}

/**
 * Picks the engine for [preference] in the TRD §4.2.2 / §4.2.4 order:
 * - AUTO: system on-device, then the system default recognizer when online, then Whisper.
 * - ON_DEVICE: system on-device, then Whisper. Never the system default recognizer, which may send audio off
 *   the phone.
 * - WHISPER: Whisper, then system on-device. Never the system default recognizer.
 */
class SpeechEngineSelector(private val caps: RecognizerCapabilities) {
    suspend fun choose(lang: String, preference: VoiceEnginePreference, online: Boolean): EngineChoice {
        val whisperEnabled = caps.whisperLangEnabled(lang)
        val whisperReady = caps.whisperModelReady(lang)
        if (preference == VoiceEnginePreference.WHISPER && whisperReady) return EngineChoice.Use(EngineKind.WHISPER)

        val service = caps.isRecognitionAvailable()
        var onDeviceUnsupported = false
        if (caps.sdkInt >= Build.VERSION_CODES.S && caps.isOnDeviceAvailable()) {
            if (caps.onDeviceSupports(lang) != false) return EngineChoice.Use(EngineKind.SYSTEM_ON_DEVICE)
            onDeviceUnsupported = true
        }
        val localOnly = preference != VoiceEnginePreference.AUTO
        if (!localOnly && service && online) return EngineChoice.Use(EngineKind.SYSTEM_DEFAULT)
        if (whisperEnabled && whisperReady) return EngineChoice.Use(EngineKind.WHISPER)

        val canDownloadSystem = caps.sdkInt >= Build.VERSION_CODES.TIRAMISU
        if (localOnly) {
            val reason = when {
                onDeviceUnsupported -> "lang_unsupported"
                !service -> "no_service"
                else -> "offline_no_model"
            }
            // Only an on-device recognizer that lacks the language can download a system model.
            return EngineChoice.Unavailable(
                reason,
                offerSystemDownload = onDeviceUnsupported && canDownloadSystem,
                offerWhisperDownload = whisperEnabled,
                localOnly = true,
            )
        }
        val reason = when {
            !service -> "no_service"
            onDeviceUnsupported -> "lang_unsupported"
            else -> "offline_no_model"
        }
        return EngineChoice.Unavailable(
            reason,
            offerSystemDownload = reason != "no_service" && canDownloadSystem,
            offerWhisperDownload = whisperEnabled,
        )
    }
}
