/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

// :core:firebase (TRD §5.2, Sprint 3 decision 6): App Check and anonymous Auth tokens for kb-api; since S4 also
// Remote Config, Analytics and Crashlytics (Sprint 4 decision 17). FirebaseApp is
// initialised by hand, on first use and only when options are configured; FirebaseInitProvider is removed from the
// merged manifest (app/src/main/AndroidManifest.xml), so nothing Firebase runs at process start. The App Check
// provider is the debug one in debug builds (src/debug) and Play Integrity otherwise (src/release, src/beta).

plugins {
    alias(libs.plugins.aikeyboard.android.library)
}

android {
    namespace = "com.manjul.aikeyboard.core.firebase"
}

dependencies {
    api(projects.core.ai)
    val firebaseBom = platform(libs.firebase.bom)
    implementation(firebaseBom)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(libs.kotlinx.coroutines.play.services)
    // S4 Task 1: Remote Config (mirrored for the IME), Analytics and Crashlytics on the default FirebaseApp
    implementation(libs.firebase.config)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    api(projects.core.common)
    api(projects.core.analytics)
    api(projects.core.datastore)

    testImplementation(libs.kotlinx.coroutines.test)
}
