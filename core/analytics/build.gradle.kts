/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

// :core:analytics (PRD §12): event catalog with the §12 limits and a batched, consent-aware relay.
// No Firebase yet: debug builds use LogcatSink, release builds NoopSink.

plugins {
    alias(libs.plugins.aikeyboard.android.library)
}

android {
    namespace = "com.manjul.aikeyboard.core.analytics"
}

dependencies {
    api(libs.kotlinx.coroutines)
    api(projects.ime.policy)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
}
