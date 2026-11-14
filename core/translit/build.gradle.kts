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

tasks.withType<Test>().configureEach {
    // SdictLexiconTest, TranslitBenchmarkTest and LexiconMemoryTest read the shipped dictionaries from the checkout.
    val dictDir = rootProject.layout.projectDirectory.dir("app/src/main/assets/dict")
    systemProperty("translit.dictDir", dictDir.asFile.absolutePath)
    inputs.dir(dictDir).withPropertyName("shippedDictionaries")
}
