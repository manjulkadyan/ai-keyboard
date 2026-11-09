/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
 * Modified by AI Keyboard contributors, 2026. FORK(S4-perf): SDK levels and JVM target from the project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    alias(libs.plugins.agp.test)
    alias(libs.plugins.kotlin.android)
}

// FORK(S4-perf): the module was off upstream. It now builds with the project's SDK levels (minSdk 26, compile and
// target 36) and JVM 11 like :app, and Kotlin's compilerOptions replaces the removed kotlinOptions block
val projectMinSdk: String by project
val projectTargetSdk: String by project
val projectCompileSdk: String by project

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

android {
    namespace = "com.manjul.aikeyboard.benchmark"
    compileSdk = projectCompileSdk.toInt() // FORK(S4-perf)

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11 // FORK(S4-perf)
        targetCompatibility = JavaVersion.VERSION_11
    }

    defaultConfig {
        minSdk = projectMinSdk.toInt() // FORK(S4-perf)
        targetSdk = projectTargetSdk.toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        sourceSets {
            maybeCreate("main").apply {
                java {
                    srcDirs("src/main/kotlin")
                }
            }
        }
    }

    buildTypes {
        // This benchmark buildType is used for benchmarking, and should function like your
        // release build (for example, with minification on). It"s signed with a debug key
        // for easy local/CI testing.
        create("benchmark") {
            isDebuggable = true
            signingConfig = getByName("debug").signingConfig
            matchingFallbacks += listOf("release")
        }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.test.ext)
    implementation(libs.androidx.test.espresso.core)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro)
}

androidComponents {
    beforeVariants(selector().all()) {
        it.enable = it.buildType == "benchmark"
    }
}
