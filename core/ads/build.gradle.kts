/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

// :core:ads (TRD §2.2, §4.11): consent (UMP), placement registry, global and frequency caps.
// App process only: :ime* modules must not depend on this module (enforced by aikeyboard.module-rules),
// and the upstream IME code inside :app must not import it (enforced by ImeAdIsolationTest in :app).
// The Google Mobile Ads SDK is not added yet; placements and ad loading arrive in Sprint 4.

plugins {
    alias(libs.plugins.aikeyboard.android.library)
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
    implementation(libs.google.ump)
}
