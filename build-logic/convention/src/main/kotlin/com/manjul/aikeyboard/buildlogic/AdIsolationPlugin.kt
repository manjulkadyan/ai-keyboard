/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.buildlogic

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test

/**
 * `aikeyboard.ad-isolation`: apply to the Android application module that hosts the IME (`:app`).
 * For each variant it registers `verify<Variant>NoAdAutoInit`, which fails when the merged manifest
 * declares an ad/consent SDK auto-init provider or `androidx.startup` initializer ([AdAutoInitRules]).
 * It also registers `verify<Variant>ExportedComponents`, which applies the exported-component allow-list
 * ([ExportedComponentRules]) to the same merged manifest, so components merged in from libraries are
 * reviewed too. The tasks are wired into `check`. It also declares the module's sources as inputs of its unit tests,
 * for the source scan in `ImeAdIsolationTest`.
 */
class AdIsolationPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.withPlugin("com.android.application") {
            val components = extensions.getByType(AndroidComponentsExtension::class.java)
            components.onVariants { variant ->
                val variantName = variant.name.replaceFirstChar { it.uppercase() }
                val task = tasks.register("verify${variantName}NoAdAutoInit", VerifyNoAdAutoInitTask::class.java) {
                    group = "verification"
                    description = "Fails if the ${variant.name} merged manifest auto-initialises an ad or consent SDK"
                    mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
                    report.set(layout.buildDirectory.file("reports/ad-isolation/${variant.name}.txt"))
                }
                val exportedTask = tasks.register("verify${variantName}ExportedComponents", VerifyExportedComponentsTask::class.java) {
                    group = "verification"
                    description = "Fails if the ${variant.name} merged manifest exports a component that is not allow-listed"
                    mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
                    report.set(layout.buildDirectory.file("reports/exported-components/${variant.name}.txt"))
                }
                tasks.named("check") { dependsOn(task, exportedTask) }
            }
        }
        // ImeAdIsolationTest (a unit test in this module) reads source files directly. Declare them as
        // inputs so a source change can never leave the test UP-TO-DATE or served from the cache.
        tasks.withType(Test::class.java).configureEach {
            inputs.files(
                fileTree("src/main") { include("**/*.kt", "**/*.java") },
                rootProject.fileTree("lib") { include("*/src/main/**/*.kt", "*/src/main/**/*.java") },
            ).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("adIsolationSources")
        }
    }
}

@CacheableTask
abstract class VerifyNoAdAutoInitTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val manifest = mergedManifest.get().asFile
        val violations = manifest.inputStream().use { AdAutoInitRules.findViolations(it) }
        val reportFile = report.get().asFile
        reportFile.parentFile.mkdirs()
        if (violations.isNotEmpty()) {
            reportFile.delete()
            throw GradleException(
                buildString {
                    appendLine("Ad/consent SDK code would run at process start, also when the IME starts the process")
                    appendLine("(TRD §2.3, §11.1). Remove it with tools:node=\"remove\" in :app's manifest. Found in $manifest:")
                    violations.forEach { appendLine("  - $it") }
                },
            )
        }
        reportFile.writeText("OK: no ad/consent auto-init entries in ${manifest.name}\n")
    }
}

@CacheableTask
abstract class VerifyExportedComponentsTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val manifest = mergedManifest.get().asFile
        val violations = manifest.inputStream().use { ExportedComponentRules.findViolations(it) }
        val reportFile = report.get().asFile
        reportFile.parentFile.mkdirs()
        if (violations.isNotEmpty()) {
            reportFile.delete()
            throw GradleException(
                buildString {
                    appendLine("Exported components must be reviewed (TRD §6.3, §7.1). Remove the export with")
                    appendLine("tools:node=\"merge\" + android:exported=\"false\" in :app's manifest, or add a reasoned entry to")
                    appendLine("ExportedComponentRules.ALLOW_LIST. Found in $manifest:")
                    violations.forEach { appendLine("  - $it") }
                },
            )
        }
        reportFile.writeText("OK: every exported component in ${manifest.name} is allow-listed\n")
    }
}
