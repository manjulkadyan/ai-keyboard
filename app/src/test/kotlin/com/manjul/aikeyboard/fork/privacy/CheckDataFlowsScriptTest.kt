/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.privacy

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * `scripts/check-data-flows.sh` (CI, after Assemble debug): every ads, consent, Firebase, billing, ML Kit and Play
 * artifact on the release runtime classpath must be named in `docs/data-flows.md`. The script runs against fixture
 * trees here (`src/test/resources/data-flows`), so the test needs no Gradle run.
 */
class CheckDataFlowsScriptTest {
    private val repoRoot: File = listOf(File("."), File(".."))
        .firstOrNull { File(it, "scripts/check-data-flows.sh").isFile }
        ?: fail("Cannot locate scripts/check-data-flows.sh from ${File(".").absolutePath}")

    private val fixtures = listOf(File("src/test/resources/data-flows"), File("app/src/test/resources/data-flows"))
        .firstOrNull { it.isDirectory } ?: fail("Cannot locate the data-flows fixtures")

    private data class Run(val code: Int, val output: String)

    private fun run(tree: File, doc: File? = null): Run {
        val process = ProcessBuilder("bash", File(repoRoot, "scripts/check-data-flows.sh").absolutePath, tree.absolutePath)
            .directory(repoRoot)
            .redirectErrorStream(true)
            .apply { if (doc != null) environment()["DATA_FLOWS_DOC"] = doc.absolutePath }
            .start()
        val output = process.inputStream.bufferedReader().readText()
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "script timed out")
        return Run(process.exitValue(), output)
    }

    @Test
    fun `the full tree passes against the real data-flows doc`() {
        val result = run(File(fixtures, "deps-full.txt"))
        assertEquals(0, result.code, result.output)
    }

    @Test
    fun `a doc missing billing fails and names each billing artifact`() {
        val doc = File.createTempFile("data-flows", ".md").apply { deleteOnExit() }
        doc.writeText(
            File(repoRoot, "docs/data-flows.md").readLines()
                .filterNot { "com.android.billingclient" in it }
                .joinToString("\n"),
        )
        val result = run(File(fixtures, "deps-full.txt"), doc)
        assertEquals(1, result.code, result.output)
        assertTrue("com.android.billingclient:billing" in result.output, result.output)
        assertTrue("com.android.billingclient:billing-ktx" in result.output, result.output)
        assertFalse("com.google.mlkit:translate" in result.output, result.output)
    }

    @Test
    fun `a tree with a new SDK artifact fails until the doc names it`() {
        val tree = File.createTempFile("deps", ".txt").apply { deleteOnExit() }
        tree.writeText(
            File(fixtures, "deps-full.txt").readText() +
                "+--- com.google.firebase:firebase-messaging:25.0.0\n",
        )
        val result = run(tree)
        assertEquals(1, result.code, result.output)
        assertTrue("com.google.firebase:firebase-messaging" in result.output, result.output)
    }

    @Test
    fun `a tree without any tracked SDK is an error, not a pass`() {
        val tree = File.createTempFile("deps", ".txt").apply { deleteOnExit() }
        tree.writeText("+--- androidx.core:core:1.0.0\n")
        val result = run(tree)
        assertEquals(2, result.code, result.output)
    }
}
