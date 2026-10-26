/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

// :core:ai (TRD §4.4, §5.3): the client side of kb-api. AiClient streams /v1/rewrite and /v1/polish over SSE,
// maps every failure to an AiError, and AiLru / AiPauseGate hold in-memory state. AiEngine lets an on-device
// engine (OD-20) slot in later. Pure Kotlin, no Android. Request and response text is user content: it is never
// logged and never part of an exception message.

plugins {
    alias(libs.plugins.aikeyboard.jvm.library)
    `java-test-fixtures`
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.okhttp)
    api(projects.core.config)
    implementation(libs.kotlinx.serialization.json)

    testFixturesApi(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockwebserver)
}
