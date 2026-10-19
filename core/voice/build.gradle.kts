/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

// :core:voice: speech session state machine, error policy and spoken punctuation. Engines (platform, Whisper)
// plug in through SpeechEngine. The Whisper engine, its model manager and the model download worker live in
// the whisper package. No transcript text is ever logged.

plugins {
    alias(libs.plugins.aikeyboard.android.library)
}

apply(from = rootProject.file("gradle/robolectric-offline.gradle.kts"))

android {
    namespace = "com.manjul.aikeyboard.core.voice"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    api(libs.kotlinx.coroutines)
    api(projects.lib.whisper)
    api(libs.androidx.work.runtime)
    implementation(projects.core.analytics)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit4)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.mockwebserver)
    testRuntimeOnly(libs.junit.vintage.engine)
}
