/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

// :core:config (Sprint 3 decision 5): the RemoteConfig interface and its defaults. Firebase Remote Config
// arrives with a Firebase project (S4); until then every value is the compiled-in default. Pure Kotlin.

plugins {
    alias(libs.plugins.aikeyboard.jvm.library)
}
