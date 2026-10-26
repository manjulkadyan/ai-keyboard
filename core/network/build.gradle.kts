/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

// :core:network (TRD §5.2): the one OkHttpClient factory and the interceptor that adds the kb-api auth headers
// from a TokenSource. No User-Agent beyond OkHttp's default and no device or install identifier (decision 7).

plugins {
    alias(libs.plugins.aikeyboard.jvm.library)
}

dependencies {
    api(libs.okhttp)
    api(projects.core.ai)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
}
