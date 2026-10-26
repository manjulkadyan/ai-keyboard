/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

// :tools:dictbuild (S3-suggest, PRD FR-05, TRD §4.1.2): builds app/src/main/assets/dict/<lang>.sdict from the open
// word lists under third_party/wordlists (plan decision 2). A developer tool, never packaged in the APK.
//   ./gradlew :tools:dictbuild:run --args="$PWD/third_party/wordlists $PWD/app/src/main/assets/dict"

plugins {
    alias(libs.plugins.aikeyboard.jvm.library)
    application
}

application {
    mainClass.set("com.manjul.aikeyboard.tools.dictbuild.MainKt")
}

dependencies {
    implementation(projects.core.suggest)
}

tasks.withType<Test>().configureEach {
    // ShippedDictionariesTest reads the committed assets and the raw lists from the repo checkout.
    val root = rootProject.layout.projectDirectory
    systemProperty("dictbuild.assetsDir", root.dir("app/src/main/assets/dict").asFile.absolutePath)
    systemProperty("dictbuild.wordlistsDir", root.dir("third_party/wordlists").asFile.absolutePath)
    inputs.dir(root.dir("app/src/main/assets/dict")).withPropertyName("shippedDictionaries")
    inputs.dir(root.dir("third_party/wordlists")).withPropertyName("wordlists")
}
