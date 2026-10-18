/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

/** JVM bytecode level shared with the upstream FlorisBoard modules. */
internal const val JAVA_TARGET = 11

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).orElseThrow { IllegalStateException("No library '$alias' in libs.versions.toml") }

/**
 * Reads an SDK level from the root gradle.properties (projectMinSdk, projectTargetSdk,
 * projectCompileSdk) so our modules always match the upstream ones.
 */
internal fun Project.intProperty(name: String): Int {
    val raw = providers.gradleProperty(name).orNull
        ?: error("Gradle property '$name' is missing (expected in the root gradle.properties)")
    return raw.toIntOrNull() ?: error("Gradle property '$name' is not an integer: '$raw'")
}

/** JUnit 5 for local unit tests, the same framework the upstream modules use. */
internal fun Project.configureJUnit5() {
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events = setOf(TestLogEvent.FAILED, TestLogEvent.SKIPPED)
        }
    }
    dependencies {
        add("testImplementation", platform(libs.lib("junit-bom")))
        add("testImplementation", libs.lib("junit-jupiter-params"))
        add("testImplementation", libs.lib("kotlin-test-junit5"))
        add("testRuntimeOnly", libs.lib("junit-jupiter-engine"))
        add("testRuntimeOnly", libs.lib("junit-platform-launcher"))
    }
}
