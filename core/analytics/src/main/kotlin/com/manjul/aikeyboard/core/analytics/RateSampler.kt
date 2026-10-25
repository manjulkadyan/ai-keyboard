/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.analytics

/** Lets through a [rate] fraction of calls (e.g. 0.01 for `translate_preview`, PRD FR-13 AC7). */
class RateSampler(private val rate: Double, private val random: () -> Double = Math::random) {
    fun sample(): Boolean = random() < rate
}
