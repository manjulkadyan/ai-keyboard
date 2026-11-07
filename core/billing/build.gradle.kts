/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

// :core:billing (TRD §4.12, FR-12): the Play Billing gateway and the entitlement cache. kb-api is the only Pro
// authority (ARCH-006): a local Play purchase never unlocks Pro by itself, the device only caches kb-api's signed
// answer. App process only: :ime* modules must not depend on this module (aikeyboard.module-rules) and IME code in
// :app must not reference it (ImeAdIsolationTest). Purchase tokens, order ids and auth headers are never logged.

plugins {
    alias(libs.plugins.aikeyboard.android.library)
}

apply(from = rootProject.file("gradle/robolectric-offline.gradle.kts"))

android {
    namespace = "com.manjul.aikeyboard.core.billing"

    buildFeatures {
        // BuildConfig.DEBUG: an empty entitlement public key is accepted in debug builds only.
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    api(libs.kotlinx.coroutines)
    api(libs.billing.ktx)
    api(projects.core.ai)
    api(projects.core.datastore)
    api(projects.core.analytics)
    api(projects.core.common)
    implementation(libs.androidx.work.runtime)

    testImplementation(testFixtures(projects.core.ai))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.androidx.work.testing)
    testRuntimeOnly(libs.junit.vintage.engine)
}
