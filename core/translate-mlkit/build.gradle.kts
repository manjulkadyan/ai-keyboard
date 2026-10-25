/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

// :core:translate-mlkit: ML Kit Translation and Language ID behind the :core:translate interfaces. ML Kit is
// initialised on first use (the merged MlKitInitProvider is removed, TRD §2.3), never at process start.
// Text is user content: never logged, and ML Kit exception messages are dropped.

plugins {
    alias(libs.plugins.aikeyboard.android.library)
}

apply(from = rootProject.file("gradle/robolectric-offline.gradle.kts"))

android {
    namespace = "com.manjul.aikeyboard.core.translate.mlkit"
}

dependencies {
    api(projects.core.translate)
    api(libs.mlkit.translate)
    implementation(libs.mlkit.language.id)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(testFixtures(projects.core.translate))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testRuntimeOnly(libs.junit.vintage.engine)
}
