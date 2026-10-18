/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.voice

class FakeSpeechEngine(
    override val kind: EngineKind = EngineKind.SYSTEM_DEFAULT,
    override val supportsPartials: Boolean = true,
) : SpeechEngine {
    var listener: SpeechEngine.Listener? = null
    var startCount = 0
    var stopCount = 0
    var cancelCount = 0
    var destroyed = false
    var startedLang: String? = null

    override fun start(lang: String, listener: SpeechEngine.Listener) {
        startCount++
        startedLang = lang
        this.listener = listener
    }

    override fun stop() { stopCount++ }
    override fun cancel() { cancelCount++ }
    override fun destroy() { destroyed = true }
}
