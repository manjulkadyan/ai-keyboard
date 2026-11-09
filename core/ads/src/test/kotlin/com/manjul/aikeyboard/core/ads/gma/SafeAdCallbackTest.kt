/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ads.gma

import com.manjul.aikeyboard.core.common.NonFatalReporter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SafeAdCallbackTest {
    private class RecordingReporter : NonFatalReporter {
        val reports = mutableListOf<Pair<String, Throwable>>()
        override fun report(area: String, error: Throwable) {
            reports += area to error
        }
    }

    @Test
    fun `a value passes through and nothing is reported`() {
        val reporter = RecordingReporter()
        assertEquals(42, safeAdCallback(reporter, "ads_test") { 42 })
        assertTrue(reporter.reports.isEmpty())
    }

    @Test
    fun `an exception becomes null and one report without its message`() {
        val reporter = RecordingReporter()
        val result: Int? = safeAdCallback(reporter, "ads_test") { throw IllegalStateException("user typed secret") }
        assertNull(result)
        val (area, error) = reporter.reports.single()
        assertEquals("ads_test", area)
        val reported = generateSequence(error) { it.cause }.toList()
        assertFalse(reported.any { it.message.orEmpty().contains("secret") }, "message leaked: $reported")
        assertFalse(reported.any { it.toString().contains("secret") }, "message leaked: $reported")
        assertTrue(error.message.orEmpty().contains("IllegalStateException"), "class name kept: ${error.message}")
        assertTrue(error.stackTrace.isNotEmpty())
    }
}
