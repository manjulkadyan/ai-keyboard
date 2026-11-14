/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

// :core:translit (Sprint 6): Roman Urdu and Hinglish transliteration core. Rule tables (TSV resources) and, in later
// tasks, the beam decoder. Pure Kotlin, no Android, no network. Typed text is user content and is never logged.

plugins {
    alias(libs.plugins.aikeyboard.jvm.library)
}

dependencies {
    implementation(projects.core.suggest)
}
