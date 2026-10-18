/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

plugins {
    `kotlin-dsl`
}

group = "com.manjul.aikeyboard.buildlogic"

dependencies {
    // Only needed to compile against the AGP / KGP DSL. At runtime the root build puts the real
    // plugins on the classpath (root build.gradle.kts declares them with "apply false").
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(gradleTestKit())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "aikeyboard.android.library"
            implementationClass = "com.manjul.aikeyboard.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "aikeyboard.android.library.compose"
            implementationClass = "com.manjul.aikeyboard.buildlogic.AndroidLibraryComposeConventionPlugin"
        }
        register("jvmLibrary") {
            id = "aikeyboard.jvm.library"
            implementationClass = "com.manjul.aikeyboard.buildlogic.JvmLibraryConventionPlugin"
        }
        register("moduleRules") {
            id = "aikeyboard.module-rules"
            implementationClass = "com.manjul.aikeyboard.buildlogic.ModuleRulesPlugin"
        }
    }
}
