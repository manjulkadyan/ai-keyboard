/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.clipboard

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Source guard for final review I2, in the style of `ImeAdIsolationTest`: every DAO call in `ClipboardManager.kt`
 * that writes text (`insert(` or `update(`) must take a value that came from `historyVault.sealForWrite`. A new write
 * path fails here until it seals, and [ClipboardManagerSealingTest] checks the stored bytes at run time.
 */
class ClipboardManagerSealGuardTest {
    private val source: String = listOf(File("."), File(".."))
        .map { File(it, "app/src/main/kotlin/com/manjul/aikeyboard/ime/clipboard/ClipboardManager.kt") }
        .firstOrNull { it.isFile }
        ?.readText()
        ?: fail("Cannot find ClipboardManager.kt from ${File(".").absolutePath}")

    private val writeCall = Regex("""clipHistoryDao\?\.(insert|update)\(((?:[^()]|\([^()]*\))*)\)""")

    @Test fun `every text write in ClipboardManager takes a sealed value`() {
        val lines = source.lines()
        val writes = lines.withIndex().filter { (_, line) -> writeCall.containsMatchIn(line) }
        assertEquals(4, writes.size, "write sites changed; review each one, then update this count")
        for ((index, line) in writes) {
            val arg = writeCall.find(line)!!.groupValues[2].trim()
            val sealedInline = line.contains("historyVault.sealForWrite(") && arg == "it"
            val sealedVal = Regex("""val\s+${Regex.escape(arg)}\s*=\s*historyVault\.sealForWrite\(""")
            val sealedAbove = arg.matches(Regex("""\w+""")) &&
                lines.subList(maxOf(0, index - 3), index).any { sealedVal.containsMatchIn(it) }
            assertTrue(sealedInline || sealedAbove, "ClipboardManager.kt:${index + 1} writes an unsealed value: $line")
        }
    }

    @Test fun `no other DAO write method is called`() {
        val other = Regex("""clipHistoryDao\?\.(insert\w+|update\w+|upsert\w*|replaceText)\(""")
        val hits = source.lines().withIndex().filter { other.containsMatchIn(it.value) }.map { it.index + 1 }
        assertEquals(emptyList(), hits, "a new DAO write in ClipboardManager.kt must go through sealForWrite")
    }
}
