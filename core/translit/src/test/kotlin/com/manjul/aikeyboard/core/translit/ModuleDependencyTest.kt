/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translit

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ModuleDependencyTest {
    @Test fun runtimeClasspathHasNoNetworkAndroidOrLoggingJars() {
        val banned = listOf("okhttp", "ktor", "android", "slf4j")
        val jars = System.getProperty("java.class.path").split(File.pathSeparator)
            .map { File(it).name.lowercase() }
            .filter { it.endsWith(".jar") }
        val hits = jars.filter { name -> banned.any { name.contains(it) } }
        assertTrue(hits.isEmpty(), "banned jars on classpath: $hits")
    }
}
