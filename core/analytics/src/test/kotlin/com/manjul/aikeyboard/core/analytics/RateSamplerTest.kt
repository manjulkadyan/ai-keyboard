/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.analytics

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RateSamplerTest {
    @Test fun onePercent() {
        assertTrue(RateSampler(0.01) { 0.005 }.sample())
        assertFalse(RateSampler(0.01) { 0.02 }.sample())
        assertFalse(RateSampler(0.01) { 0.01 }.sample())
    }

    @Test fun edges() {
        assertFalse(RateSampler(0.0) { 0.0 }.sample())
        assertTrue(RateSampler(1.0) { 0.999999 }.sample())
    }
}
