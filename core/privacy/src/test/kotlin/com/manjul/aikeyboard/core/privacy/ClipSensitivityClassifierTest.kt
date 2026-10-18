/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.privacy

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClipSensitivityClassifierTest {
    @Test
    fun `flagged clips are sensitive whatever the text`() {
        assertTrue(ClipSensitivityClassifier.isSensitive("hello world", flaggedSensitive = true))
        assertTrue(ClipSensitivityClassifier.isSensitive(null, flaggedSensitive = true))
    }

    @ParameterizedTest
    @ValueSource(strings = ["482913", "Your code is 482913", "G-482913", "1234", "12345678", "  482913\n"])
    fun `otp-like text is sensitive`(text: String) {
        assertTrue(ClipSensitivityClassifier.isSensitive(text, false), text)
    }

    @ParameterizedTest
    @ValueSource(strings = ["Tr0ub4dor&3xK!", "aB3\$eFgH1jK"])
    fun `password-like token is sensitive`(text: String) {
        assertTrue(ClipSensitivityClassifier.isSensitive(text, false), text)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "hello world", "call me at 5", "https://example.com", "2026-10-18", "123", "123456789",
            "https://example.com/a?b=C3d&e=F!", "Hello, World 2026!", "meet at 10:30 on 18.10.2026",
            "short1A!", "",
        ],
    )
    fun `ordinary text is not sensitive`(text: String) {
        assertFalse(ClipSensitivityClassifier.isSensitive(text, false), text)
    }

    @Test
    fun `null is not sensitive`() {
        assertFalse(ClipSensitivityClassifier.isSensitive(null, false))
    }
}
