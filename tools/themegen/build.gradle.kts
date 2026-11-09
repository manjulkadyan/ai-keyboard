/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

// :tools:themegen (S4-themes, plan Task 9): writes the com.manjul.aikeyboard.themes extension (extension.json and
// stylesheets/<id>{,_borderless}.json) from :core:theme. A developer tool, never packaged in the APK.
//   ./gradlew :tools:themegen:run --args="$PWD/app/src/main/assets/ime/theme/com.manjul.aikeyboard.themes"

plugins {
    alias(libs.plugins.aikeyboard.jvm.library)
    application
}

application {
    mainClass.set("com.manjul.aikeyboard.tools.themegen.MainKt")
}

dependencies {
    implementation(projects.core.theme)
}
