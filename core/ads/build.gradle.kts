/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

// :core:ads (TRD §2.2, §4.11): consent (UMP), placement registry, global and frequency caps.
// App process only: :ime* modules must not depend on this module (enforced by aikeyboard.module-rules),
// and the upstream IME code inside :app must not import it (enforced by ImeAdIsolationTest in :app).
// S4 Task 6: the Google Mobile Ads SDK (gma/) starts only after UMP consent, from the app's main Activity.
// Its init provider is removed in :app's manifest, so nothing here runs at process start (TRD §11.1).

plugins {
    alias(libs.plugins.aikeyboard.android.library)
    alias(libs.plugins.kotlin.serialization) // S4 Task 7: ads_state.json
}

android {
    namespace = "com.manjul.aikeyboard.core.ads"

    testOptions {
        // AdsInitializerTest creates a bare Activity from the android.jar stubs.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(libs.kotlinx.coroutines)
    api(projects.core.common)
    implementation(projects.core.datastore)
    implementation(projects.core.config) // S4 Task 7: ad unit ids from Remote Config
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.google.ump)
    implementation(libs.play.services.ads)
    testImplementation(libs.kotlinx.coroutines.test)
}
