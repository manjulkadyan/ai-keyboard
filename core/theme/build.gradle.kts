/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

// :core:theme (S4-themes, plan Task 9): ThemeSpec, the 12 original presets and the generator that writes Snygg v2
// stylesheets and the theme extension manifest. Pure Kotlin. :tools:themegen writes the output into the app assets.

plugins {
    alias(libs.plugins.aikeyboard.jvm.library)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
