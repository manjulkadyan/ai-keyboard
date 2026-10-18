/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

// :core:datastore (TRD §2.2): fork-owned settings (FeatureSettings) in a multi-process DataStore.
// Upstream FlorisBoard preferences stay in jetpref.

plugins {
    alias(libs.plugins.aikeyboard.android.library)
    alias(libs.plugins.kotlin.serialization)
}

apply(from = rootProject.file("gradle/robolectric-offline.gradle.kts"))

android {
    namespace = "com.manjul.aikeyboard.core.datastore"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    api(libs.kotlinx.coroutines)
    api(libs.kotlinx.serialization.json)
    api(libs.androidx.datastore)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testRuntimeOnly(libs.junit.vintage.engine)
}
