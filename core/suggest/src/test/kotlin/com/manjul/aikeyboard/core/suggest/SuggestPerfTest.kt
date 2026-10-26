/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.suggest

import com.manjul.aikeyboard.core.suggest.testing.InMemoryLearnedWords
import com.manjul.aikeyboard.core.suggest.testing.TestDictionaries
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * JVM guard for TRD §6.4: 1,000 random 1-8 char queries against a 50,000-word dictionary have p90 < 10 ms.
 * The device budget is 30 ms p90 and is checked on device, not here.
 */
class SuggestPerfTest {
    @Test fun p90Under10msOn50kWords() {
        val engine = SuggestEngine(TestDictionaries.synthetic(50_000), KeyProximity.QWERTY, InMemoryLearnedWords())
        val random = Random(1234)
        fun query(): String {
            val length = random.nextInt(1, 9)
            return buildString(length) { repeat(length) { append('a' + random.nextInt(26)) } }
        }
        repeat(300) { engine.suggest(query(), "the") } // JIT warm-up

        val timesNs = LongArray(1_000) {
            val q = query()
            val start = System.nanoTime()
            engine.suggest(q, "the")
            System.nanoTime() - start
        }
        timesNs.sort()
        val p90Ms = timesNs[(timesNs.size * 9 / 10) - 1] / 1_000_000.0
        assertTrue(p90Ms < 10.0, "p90 was $p90Ms ms")
    }
}
