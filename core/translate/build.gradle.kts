/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

// :core:translate (TRD §4.3): engine-agnostic translation logic. Engine interface, router, LRU cache,
// sentence chunking, source-language rules, translator pooling and the debounced bar pipeline. Pure
// Kotlin, no Android: ML Kit plugs in from :core:translate-mlkit. Text is user content and is never logged.

plugins {
    alias(libs.plugins.aikeyboard.jvm.library)
    `java-test-fixtures`
}

dependencies {
    api(libs.kotlinx.coroutines.core)

    testFixturesApi(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
