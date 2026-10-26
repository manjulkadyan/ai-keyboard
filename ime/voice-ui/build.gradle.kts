/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

// :ime:voice-ui: voice typing inside the keyboard (controller, panel). Depends on :core:voice, :core:analytics,
// :ime:policy and :ime:editing (composing span) only; the IME service in :app wires it to the editor and the platform.

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
    api(projects.ime.editing)
    implementation(libs.androidx.compose.material3)

    testImplementation(testFixtures(projects.ime.editing))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // A library's unit tests may declare the empty host activity; nothing here ships in an app.
    testImplementation(libs.androidx.compose.ui.test.manifest)
    testRuntimeOnly(libs.junit.vintage.engine)
}
