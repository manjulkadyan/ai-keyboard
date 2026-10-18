/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.privacy

/**
 * Heuristic for clips that must never be stored: one-time codes and password-like tokens, plus anything the
 * source app flagged as sensitive. Never logs or retains the text.
 */
object ClipSensitivityClassifier {
    private const val MAX_OTP_SENTENCE_LENGTH = 60
    private const val MIN_PASSWORD_LENGTH = 10

    /** Bare code, optionally with a short alphabetic prefix such as `G-482913`. */
    private val bareOtp = Regex("""^(?:[A-Za-z]{1,4}[- ])?\d{4,8}$""")
    private val digitRun = Regex("""(?<![\d.:/-])\d{4,8}(?![\d.:/-])""")
    private val otpKeyword = Regex("""(?i)\b(code|otp|pin|passcode|password|verification|one-time|token)\b""")

    fun isSensitive(text: String?, flaggedSensitive: Boolean): Boolean {
        if (flaggedSensitive) return true
        val trimmed = text?.trim()
        if (trimmed.isNullOrEmpty()) return false
        return looksLikeOtp(trimmed) || looksLikePassword(trimmed)
    }

    private fun looksLikeOtp(text: String): Boolean {
        if (bareOtp.matches(text)) return true
        return text.length <= MAX_OTP_SENTENCE_LENGTH && otpKeyword.containsMatchIn(text) && digitRun.containsMatchIn(text)
    }

    private fun looksLikePassword(text: String): Boolean {
        if (text.length < MIN_PASSWORD_LENGTH || text.any { it.isWhitespace() } || "://" in text) return false
        return text.any { it.isUpperCase() } && text.any { it.isLowerCase() } &&
            text.any { it.isDigit() } && text.any { !it.isLetterOrDigit() }
    }
}
