/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.analytics

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionSamplerTest {
    @Test fun oncePerTenMinutes() {
        var now = 1_000L
        val s = SessionSampler { now }
        assertTrue(s.shouldLogImeSession())
        now += 9 * 60_000L
        assertFalse(s.shouldLogImeSession())
        now = 1_000L + 10 * 60_000L
        assertTrue(s.shouldLogImeSession())
        now += 1
        assertFalse(s.shouldLogImeSession())
    }
}
