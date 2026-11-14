/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translit

import org.junit.jupiter.api.TestReporter
import kotlin.test.Test
import kotlin.test.assertTrue

/** Retained heap of a loaded `ur` lexicon (plan decision 6). Task 7 copies the printed number into the checklist. */
class LexiconMemoryTest {
    private fun usedAfterGc(): Long {
        val rt = Runtime.getRuntime()
        repeat(3) {
            System.gc()
            Thread.sleep(50)
        }
        return rt.totalMemory() - rt.freeMemory()
    }

    @Test fun urduLexiconStaysUnderEightMegabytes(reporter: TestReporter) {
        usedAfterGc() // settle class loading and earlier tests
        val before = usedAfterGc()
        val lexicon = TestLexicons.shipped(TranslitLang.UR)
        val after = usedAfterGc()
        // Keep the lexicon reachable until after the measurement.
        assertTrue(lexicon.prefixExists("ک"))
        val deltaMb = (after - before) / (1024.0 * 1024.0)
        val line = "LexiconMemory lang=ur heapDeltaMb=${"%.2f".format(deltaMb)}"
        println(line)
        reporter.publishEntry("heapDeltaMb", "%.2f".format(deltaMb))
        assertTrue(deltaMb < 8.0, line)
    }
}
