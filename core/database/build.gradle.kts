/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

// :core:database: the fork's Room database (TRD §3.1): Translator history and the user dictionary (user_word). Schemas are exported
// to core/database/schemas/ and every version change needs a Migration: fallbackToDestructiveMigration is
// banned (TRD §2.3; DestructiveMigrationBanTest enforces it for this module). The file lives in databases/,
// which the backup rules never include (user content, TRD §6.3).

plugins {
    alias(libs.plugins.aikeyboard.android.library)
    alias(libs.plugins.ksp)
}

apply(from = rootProject.file("gradle/robolectric-offline.gradle.kts"))

android {
    namespace = "com.manjul.aikeyboard.core.database"
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    api(libs.kotlinx.coroutines)
    api(libs.androidx.room.runtime)
    // UserWordRepository implements the suggestion engine's LearnedWords (user_word, PRD FR-05).
    api(projects.core.suggest)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit4)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    // MigrationTest reads the exported schema JSON.
    testImplementation(libs.kotlinx.serialization.json)
    testRuntimeOnly(libs.junit.vintage.engine)
}
