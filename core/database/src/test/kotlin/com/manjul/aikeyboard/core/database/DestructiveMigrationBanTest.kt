/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.database

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `fallbackToDestructiveMigration` is banned (TRD §2.3): a missing migration must fail, never wipe the user's
 * history, clipboard or dictionary. Scans every main source set in the repository, so it also covers the
 * upstream databases in :app.
 */
class DestructiveMigrationBanTest {
    private val repoRoot = File("../..").canonicalFile

    @Test
    fun `no main source calls fallbackToDestructiveMigration`() {
        assertTrue(File(repoRoot, "settings.gradle.kts").isFile, "unexpected working directory $repoRoot")
        val offenders = repoRoot.walkTopDown()
            .onEnter { it.name != "build" && it.name != ".git" && it.name != ".gradle" && it.name != ".claude" }
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .filter { it.invariantSeparatorsPath.contains("/src/main/") }
            .filter { file -> file.useLines { lines -> lines.any { CALL.containsMatchIn(it) } } }
            .map { it.relativeTo(repoRoot).path }
            .toList()
        assertTrue(offenders.isEmpty(), "fallbackToDestructiveMigration is banned: $offenders")
    }

    private companion object {
        val CALL = Regex("""\.fallbackToDestructiveMigration\w*\s*\(""")
    }
}
