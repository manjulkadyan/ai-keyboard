/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.aiui

import com.manjul.aikeyboard.core.ai.AiError
import com.manjul.aikeyboard.core.ai.RewriteMode
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.jupiter.api.Test

class AiStringsTest {
    private val hour = 3_600_000L

    @Test
    fun `quota hours round up and are at least one`() {
        assertEquals(1, AiStrings.quotaHours(resetsAtEpochMs = 1_000L, nowMs = 0L))
        assertEquals(1, AiStrings.quotaHours(resetsAtEpochMs = 0L, nowMs = 5_000L))
        assertEquals(1, AiStrings.quotaHours(resetsAtEpochMs = hour, nowMs = 0L))
        assertEquals(2, AiStrings.quotaHours(resetsAtEpochMs = hour + 1, nowMs = 0L))
        assertEquals(24, AiStrings.quotaHours(resetsAtEpochMs = 24 * hour, nowMs = 0L))
    }

    @Test
    fun `every tone, error and reason has copy`() {
        assertEquals(RewriteMode.entries.size, RewriteMode.entries.map(AiStrings::tone).toSet().size)
        assertEquals(ReportReason.entries.size, ReportReason.entries.map(AiStrings::reportReason).toSet().size)
        assertNotEquals(AiStrings.error(AiError.Offline), AiStrings.error(AiError.Timeout))
        assertEquals(R.string.ai_sheet__error_rate_limited, AiStrings.error(AiError.RateLimited(5)))
        assertEquals(R.string.ai_sheet__error_unavailable, AiStrings.error(AiError.NotConfigured))
    }
}
