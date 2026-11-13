/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

// :core:content (S5-content, plan Task 2, decisions 3 to 5): the signed content manifest and its 24 h cache, the
// content sources (CDN over HTTP, or app assets in debug builds), the SHA-256 checked pack downloader and its
// WorkManager worker, and HeadwordNorm (a deliberate copy of :tools:packbuild's, plan F5). Content requests carry no
// user identifier: no auth header, no App Check token, no cookie and no query string (FR-23 AC4).

plugins {
    alias(libs.plugins.aikeyboard.android.library)
}

apply(from = rootProject.file("gradle/robolectric-offline.gradle.kts"))

android {
    namespace = "com.manjul.aikeyboard.core.content"
}

dependencies {
    api(libs.kotlinx.coroutines)
    api(projects.core.common) // Outcome (plan F2)
    api(projects.core.database)
    api(projects.core.datastore)
    api(projects.core.analytics)
    api(projects.core.network)
    api(projects.core.ai)
    api(libs.androidx.work.runtime)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.mockwebserver)
    testRuntimeOnly(libs.junit.vintage.engine)
}

tasks.withType<Test>().configureEach {
    // The tests read the Task 1 fixture content and its test-only signing key in place (plan F15), never a copy.
    val root = rootProject.layout.projectDirectory
    val contentDir = root.dir("app/src/debug/assets/content/v1")
    val fixtureKey = root.file("tools/packbuild/src/test/resources/fixture-signing-key.pem")
    systemProperty("content.contentDir", contentDir.asFile.absolutePath)
    systemProperty("content.fixtureKeyPem", fixtureKey.asFile.absolutePath)
    inputs.dir(contentDir).withPropertyName("fixtureContent")
    inputs.file(fixtureKey).withPropertyName("fixtureKey")
}
