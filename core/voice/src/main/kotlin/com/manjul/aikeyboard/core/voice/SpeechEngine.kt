/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.voice

enum class EngineKind(val analytics: String) {
    SYSTEM_ON_DEVICE("system_on_device"),
    SYSTEM_DEFAULT("system_default"),
    WHISPER("whisper"),
}

/** A speech recognizer. Listener callbacks must arrive on the thread the owning [SpeechSession] scope runs on. */
interface SpeechEngine {
    val kind: EngineKind
    val supportsPartials: Boolean

    fun start(lang: String, listener: Listener)
    fun stop()
    fun cancel()
    fun destroy()

    interface Listener {
        fun onReady()
        fun onRms(db: Float)
        fun onPartial(text: String)
        fun onSegment(text: String)
        fun onFinal(text: String)
        fun onEndOfSpeech()

        /** [code] is an android.speech.SpeechRecognizer.ERROR_* value. */
        fun onError(code: Int)
    }
}
