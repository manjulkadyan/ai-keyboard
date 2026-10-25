/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

// :ime:translate-ui: the in-keyboard Translate bar (controller, bar buffer, Compose bar). Depends on
// :core:translate, :core:analytics, :core:datastore (BarMode), :ime:policy and :ime:editing (composing span,
// undo) only; the IME service in :app wires it to the editor. Bar text is user content and is never logged.

plugins {
    alias(libs.plugins.aikeyboard.android.library.compose)
}

apply(from = rootProject.file("gradle/robolectric-offline.gradle.kts"))

android {
    namespace = "com.manjul.aikeyboard.ime.translateui"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    api(projects.core.translate)
    api(projects.core.analytics)
    api(projects.core.datastore)
    api(projects.ime.policy)
    api(projects.ime.editing)
    implementation(libs.androidx.compose.material3)

    // JUnit 5 (jupiter, params, kotlin-test) comes from the convention plugin.
    testImplementation(testFixtures(projects.core.translate))
    testImplementation(testFixtures(projects.ime.editing))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // A library's unit tests may declare the empty host activity; nothing here ships in an app.
    testImplementation(libs.androidx.compose.ui.test.manifest)
    testRuntimeOnly(libs.junit.vintage.engine)
}
