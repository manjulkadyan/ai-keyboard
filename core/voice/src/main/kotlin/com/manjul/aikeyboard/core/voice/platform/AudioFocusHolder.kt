/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.voice.platform

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/** Holds transient, ducking audio focus while dictating. [release] is idempotent. */
class AudioFocusHolder(private val audioManager: AudioManager) {
    private var request: AudioFocusRequest? = null

    val isHeld: Boolean get() = request != null

    fun acquire(): Boolean {
        if (request != null) return true
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener { }
            .build()
        val granted = audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (granted) request = req
        return granted
    }

    fun release() {
        val req = request ?: return
        request = null
        audioManager.abandonAudioFocusRequest(req)
    }
}
