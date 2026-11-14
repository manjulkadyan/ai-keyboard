/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

// :ime:stickers-ui: the in-keyboard sticker panel (controller, panel state, Compose panel, premium card).
// Depends on :core:content (StickerCatalog, StickerUi), :core:analytics and :ime:policy only; the IME service in
// :app wires it to the editor and does the insert. No ads or billing module may be reached from here.

plugins {
    alias(libs.plugins.aikeyboard.android.library.compose)
}

android {
    namespace = "com.manjul.aikeyboard.ime.stickersui"
}

dependencies {
    api(projects.core.content)
    api(projects.core.analytics)
    api(projects.ime.policy)
    implementation(libs.androidx.compose.material3)

    // JUnit 5 (jupiter, params, kotlin-test) comes from the convention plugin.
    testImplementation(libs.kotlinx.coroutines.test)
}
