/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.aiui

import androidx.annotation.StringRes
import com.manjul.aikeyboard.core.ai.AiError
import com.manjul.aikeyboard.core.ai.RewriteMode

/**
 * The sheet's copy (Sprint 3 decision 16). Raw exception or server text is never shown, and no string names the AI
 * engine (PRD FR-15 AC11).
 */
object AiStrings {
    private const val HOUR_MS = 3_600_000L

    @StringRes
    fun tone(mode: RewriteMode): Int = when (mode) {
        RewriteMode.FIX -> R.string.ai_sheet__tone_fix
        RewriteMode.FORMAL -> R.string.ai_sheet__tone_formal
        RewriteMode.FRIENDLY -> R.string.ai_sheet__tone_friendly
        RewriteMode.SHORTER -> R.string.ai_sheet__tone_shorter
        RewriteMode.LONGER -> R.string.ai_sheet__tone_longer
        RewriteMode.EMOJIFY -> R.string.ai_sheet__tone_emojify
        RewriteMode.TRANSLATE -> R.string.ai_sheet__tone_translate
    }

    /** The error line. [AiError.Quota] has the quota card instead; [AiError.RateLimited] takes its seconds. */
    @StringRes
    fun error(error: AiError): Int = when (error) {
        AiError.Offline -> R.string.ai_sheet__error_offline
        AiError.Timeout -> R.string.ai_sheet__error_timeout
        is AiError.RateLimited -> R.string.ai_sheet__error_rate_limited
        AiError.Refused -> R.string.ai_sheet__error_refused
        AiError.TooLong -> R.string.ai_sheet__error_too_long
        AiError.Paused, AiError.Unavailable, AiError.NotConfigured, is AiError.Quota ->
            R.string.ai_sheet__error_unavailable
    }

    @StringRes
    fun reportReason(reason: ReportReason): Int = when (reason) {
        ReportReason.OFFENSIVE -> R.string.ai_sheet__report_offensive
        ReportReason.WRONG -> R.string.ai_sheet__report_wrong
        ReportReason.UNSAFE -> R.string.ai_sheet__report_unsafe
        ReportReason.OTHER -> R.string.ai_sheet__report_other
    }

    /** Whole hours until the quota resets, rounded up, at least 1 ("resets in N h"). */
    fun quotaHours(resetsAtEpochMs: Long, nowMs: Long): Int {
        val left = (resetsAtEpochMs - nowMs).coerceAtLeast(0L)
        return ((left + HOUR_MS - 1) / HOUR_MS).coerceIn(1L, 24L * 31).toInt()
    }
}
