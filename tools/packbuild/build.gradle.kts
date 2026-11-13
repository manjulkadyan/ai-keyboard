/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

// :tools:packbuild (S5-content, plan Task 1): builds the content packs from content/: the dictionary pack (SQLite),
// the phrasebook DB (SQLite), the generated sticker art (PNG), and the content manifest with its ECDSA P-256
// signature. A developer tool, never packaged in the APK.
//   ./gradlew :tools:packbuild:run --args="dict $PWD/content/dictionary/en-fixture.jsonl \
//     $PWD/app/src/debug/assets/content/v1/dict/en-fixture-1.db --lang en --version 1"

plugins {
    alias(libs.plugins.aikeyboard.jvm.library)
    application
}

application {
    mainClass.set("com.manjul.aikeyboard.tools.packbuild.MainKt")
    // Sticker art is drawn with java.awt, which must never open a window or a dock icon.
    applicationDefaultJvmArgs = listOf("-Djava.awt.headless=true")
}

dependencies {
    implementation(libs.sqlite.jdbc)
    implementation(libs.kotlinx.serialization.json)
}

tasks.withType<Test>().configureEach {
    jvmArgs("-Djava.awt.headless=true")
    // ShippedContentTest reads the committed outputs and PhrasebookBuilderTest the committed sources. Other modules
    // that read the fixture packs pass their paths the same way (plan F15).
    val root = rootProject.layout.projectDirectory
    systemProperty("packbuild.sourcesDir", root.dir("content").asFile.absolutePath)
    systemProperty("packbuild.mainAssetsDir", root.dir("app/src/main/assets").asFile.absolutePath)
    systemProperty("packbuild.contentDir", root.dir("app/src/debug/assets/content/v1").asFile.absolutePath)
    inputs.dir(root.dir("content")).withPropertyName("contentSources")
    inputs.dir(root.dir("app/src/main/assets/phrasebook")).withPropertyName("shippedPhrasebook")
    inputs.dir(root.dir("app/src/main/assets/stickers")).withPropertyName("shippedStickers")
    inputs.dir(root.dir("app/src/debug/assets/content/v1")).withPropertyName("shippedContent")
}
