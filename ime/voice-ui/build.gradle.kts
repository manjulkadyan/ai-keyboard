/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

// :ime:voice-ui: voice typing inside the keyboard (controller, composing span, panel). Depends on :core:voice,
// :core:analytics and :ime:policy only; the IME service in :app wires it to the editor and the platform.

plugins {
    alias(libs.plugins.aikeyboard.android.library.compose)
}

apply(from = rootProject.file("gradle/robolectric-offline.gradle.kts"))

android {
    namespace = "com.manjul.aikeyboard.ime.voiceui"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    api(projects.core.voice)
    api(projects.core.analytics)
    api(projects.ime.policy)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testRuntimeOnly(libs.junit.vintage.engine)
}
