/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Custom Android lint checks (TRD §2.3 "Process termination", §6.3). Consumed by :app through
// `lintChecks(projects.lintRules)`. Not an Android module: lint loads this jar into its own JVM.
plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        // Lint (AGP 8.x) runs on JDK 17+, so the checks may target 17.
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Provided by lint at runtime; never bundled into the jar.
    compileOnly(libs.android.lint.api)
    compileOnly(libs.android.lint.checks)

    testImplementation(libs.android.lint.api)
    testImplementation(libs.android.lint.checks)
    testImplementation(libs.android.lint.tests)
    testImplementation(libs.junit4)
}

tasks.jar {
    manifest {
        attributes("Lint-Registry-v2" to "com.manjul.aikeyboard.lint.AiKeyboardIssueRegistry")
    }
}

// LintDetectorTest is JUnit 4 based.
tasks.withType<Test>().configureEach {
    useJUnit()
    // The IntelliJ core environment otherwise initialises AppKit on macOS and can hang the test JVM.
    systemProperty("java.awt.headless", "true")
}
