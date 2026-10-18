/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

// :core:voice: speech session state machine, error policy and spoken punctuation. Engines (platform, Whisper)
// plug in through SpeechEngine. No transcript text is ever logged.

plugins {
    alias(libs.plugins.aikeyboard.android.library)
}

android {
    namespace = "com.manjul.aikeyboard.core.voice"
}

dependencies {
    api(libs.kotlinx.coroutines)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit4)
    testImplementation(libs.turbine)
    testRuntimeOnly(libs.junit.vintage.engine)
}
