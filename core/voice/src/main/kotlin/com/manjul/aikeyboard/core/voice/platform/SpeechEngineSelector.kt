/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.voice.platform

import android.os.Build
import com.manjul.aikeyboard.core.voice.EngineKind

sealed interface EngineChoice {
    data class Use(val kind: EngineKind) : EngineChoice

    /** [reason] is one of no_service, lang_unsupported, offline_no_model. */
    data class Unavailable(
        val reason: String,
        val offerSystemDownload: Boolean,
        val offerWhisperDownload: Boolean,
    ) : EngineChoice
}

/** Picks the engine in the TRD §4.2.2 / §4.2.4 order. The caller maps its engine preference to [preferWhisper]. */
class SpeechEngineSelector(private val caps: RecognizerCapabilities) {
    suspend fun choose(lang: String, preferWhisper: Boolean, online: Boolean): EngineChoice {
        val whisperReady = caps.whisperModelReady(lang)
        if (preferWhisper && whisperReady) return EngineChoice.Use(EngineKind.WHISPER)

        val service = caps.isRecognitionAvailable()
        var onDeviceUnsupported = false
        if (caps.sdkInt >= Build.VERSION_CODES.S && caps.isOnDeviceAvailable()) {
            if (caps.onDeviceSupports(lang) != false) return EngineChoice.Use(EngineKind.SYSTEM_ON_DEVICE)
            onDeviceUnsupported = true
        }
        if (service && online) return EngineChoice.Use(EngineKind.SYSTEM_DEFAULT)
        if (caps.whisperLangEnabled(lang) && whisperReady) return EngineChoice.Use(EngineKind.WHISPER)

        val offerWhisper = caps.whisperLangEnabled(lang)
        val canDownloadSystem = caps.sdkInt >= Build.VERSION_CODES.TIRAMISU
        val reason = when {
            !service -> "no_service"
            onDeviceUnsupported -> "lang_unsupported"
            else -> "offline_no_model"
        }
        return EngineChoice.Unavailable(reason, offerSystemDownload = reason != "no_service" && canDownloadSystem, offerWhisperDownload = offerWhisper)
    }
}
