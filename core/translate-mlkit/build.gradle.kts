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
    // Offline packs (S2-packs): the model store's WorkManager job, settings and pack analytics
    api(libs.androidx.work.runtime)
    api(projects.core.datastore)
    api(projects.core.analytics)

    testImplementation(testFixtures(projects.core.translate))
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testRuntimeOnly(libs.junit.vintage.engine)

    // Device-only checks: day-1 latency (TRD §8, R-03) and the Task 12 quality eval; built in CI, run by hand on a phone.
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.junit4)
}
