/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

plugins {
    alias(libs.plugins.aikeyboard.android.library)
}

android {
    namespace = "com.manjul.aikeyboard.core.common"
}

dependencies {
    api(libs.kotlinx.coroutines)
    // Qualifier annotations only; Hilt itself is wired in a later step.
    api(libs.javax.inject)

    testImplementation(libs.kotlinx.coroutines.test)
}
