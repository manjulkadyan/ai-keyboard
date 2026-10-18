/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency

/**
 * `aikeyboard.module-rules`: apply to the root project. After all projects are evaluated it collects
 * every project-to-project dependency (any configuration) and fails the build when [ModuleRules]
 * finds a violation.
 *
 * Note: the upstream IME still lives in `:app`, so this rule cannot by itself keep ad code out of
 * the keyboard. That is guarded by a class-reference test added with `:core:ads`.
 */
class ModuleRulesPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        require(target == target.rootProject) {
            "aikeyboard.module-rules must be applied to the root project, not ${target.path}"
        }
        target.gradle.projectsEvaluated {
            val graph = target.rootProject.allprojects.associate { project ->
                project.path to project.configurations
                    .flatMap { configuration ->
                        configuration.dependencies.withType(ProjectDependency::class.java).map { it.path }
                    }
                    .toSet()
            }
            val violations = ModuleRules.check(graph)
            if (violations.isNotEmpty()) {
                throw GradleException(
                    buildString {
                        appendLine("Module dependency rules violated (TRD §2.2, see docs/FORK_PATCHES.md):")
                        violations.forEach { appendLine("  - $it") }
                    },
                )
            }
        }
    }
}
