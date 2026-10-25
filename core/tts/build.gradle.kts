/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

// :core:tts: one shared, ref-counted Android TextToSpeech per process (PRD FR-26, TRD §4.6) and the sentence
// chunker that keeps each utterance under the engine's input limit. Spoken text is user content: never logged.

plugins {
    alias(libs.plugins.aikeyboard.android.library)
}

apply(from = rootProject.file("gradle/robolectric-offline.gradle.kts"))

android {
    namespace = "com.manjul.aikeyboard.core.tts"
}

dependencies {
    api(libs.kotlinx.coroutines)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testRuntimeOnly(libs.junit.vintage.engine)
}
