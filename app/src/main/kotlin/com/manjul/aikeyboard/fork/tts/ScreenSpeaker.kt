/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.tts

import android.content.Intent
import com.manjul.aikeyboard.core.tts.TtsAvailability
import com.manjul.aikeyboard.core.tts.TtsManager
import java.util.Locale

/**
 * One screen's or sheet's hold on the shared [TtsManager]: it acquires the engine on the first [speak] and gives
 * that one reference back on [release]. [stop] and [release] do nothing before the first speak. Used by the
 * Dictionary and the Phrasebook. Spoken text is never logged.
 */
internal class ScreenSpeaker(private val tts: TtsManager) {
    private var acquired = false

    suspend fun speak(text: String, locale: Locale): TtsAvailability {
        if (!acquired) {
            acquired = true
            tts.acquire()
        }
        val availability = runCatching { tts.availability(locale) }.getOrDefault(TtsAvailability.NOT_SUPPORTED)
        if (availability == TtsAvailability.AVAILABLE) tts.speak(text, locale)
        return availability
    }

    fun installVoiceIntent(): Intent = tts.installVoiceIntent()

    fun stop() {
        if (acquired) tts.stop()
    }

    fun release() {
        if (acquired) {
            acquired = false
            tts.release()
        }
    }
}
