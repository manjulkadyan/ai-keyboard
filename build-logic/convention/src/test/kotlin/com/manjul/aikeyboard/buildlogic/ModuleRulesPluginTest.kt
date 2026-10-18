/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.buildlogic

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Gradle TestKit check that the plugin is wired into a real build and fails it. */
class ModuleRulesPluginTest {
    @TempDir
    lateinit var projectDir: File

    private fun writeBuild(imeDependency: String) {
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            rootProject.name = "rules-fixture"
            include(":ime:policy", ":core:common", ":core:ads")
            """.trimIndent(),
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins { id("aikeyboard.module-rules") }
            subprojects { apply(plugin = "java-library") }
            """.trimIndent(),
        )
        listOf("ime/policy", "core/common", "core/ads").forEach { projectDir.resolve(it).mkdirs() }
        projectDir.resolve("ime/policy/build.gradle.kts").writeText(
            """
            dependencies { "implementation"(project("$imeDependency")) }
            """.trimIndent(),
        )
    }

    private fun runner() = GradleRunner.create()
        .withProjectDir(projectDir)
        .withPluginClasspath()
        .withArguments("help", "--stacktrace")

    @Test
    fun `build passes when ime depends on an allowed core module`() {
        writeBuild(":core:common")
        runner().build()
    }

    @Test
    fun `build fails when ime depends on core ads`() {
        writeBuild(":core:ads")
        val result = runner().buildAndFail()
        assertTrue(result.output.contains("Module dependency rules violated"), result.output)
        assertTrue(result.output.contains(":ime:policy -> :core:ads"), result.output)
    }
}
