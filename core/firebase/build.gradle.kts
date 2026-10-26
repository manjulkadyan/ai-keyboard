/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

// :core:firebase (TRD §5.2, Sprint 3 decision 6): App Check and anonymous Auth tokens for kb-api. FirebaseApp is
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

    testImplementation(libs.kotlinx.coroutines.test)
}
