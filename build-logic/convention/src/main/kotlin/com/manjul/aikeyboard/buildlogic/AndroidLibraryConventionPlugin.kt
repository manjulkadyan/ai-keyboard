/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * `aikeyboard.android.library`: Android library + Kotlin, SDK levels from gradle.properties,
 * Java 11 bytecode and JUnit 5 unit tests. Each module still sets its own `android.namespace`.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<LibraryExtension> {
            compileSdk = intProperty("projectCompileSdk")
            defaultConfig {
                minSdk = intProperty("projectMinSdk")
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            testOptions {
                targetSdk = intProperty("projectTargetSdk")
                unitTests.all { it.useJUnitPlatform() }
            }
            lint {
                targetSdk = intProperty("projectTargetSdk")
            }
            buildTypes {
                // :app has a "beta" build type; upstream libraries declare it too so variants match.
                maybeCreate("beta")
            }
            compileOptions {
                sourceCompatibility = JavaVersion.toVersion(JAVA_TARGET)
                targetCompatibility = JavaVersion.toVersion(JAVA_TARGET)
            }
        }

        extensions.configure<KotlinAndroidProjectExtension> {
            compilerOptions {
                jvmTarget.set(JvmTarget.fromTarget(JAVA_TARGET.toString()))
            }
        }

        configureJUnit5()
    }
}
