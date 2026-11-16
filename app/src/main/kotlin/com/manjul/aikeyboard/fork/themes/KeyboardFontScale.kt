/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.themes

import androidx.compose.ui.unit.Density

/**
 * Key labels live in fixed-size key cells, so the system font scale must not grow them without bound.
 * At scale 2.0 "?123" was clipped to "?" and hint symbols overlapped the letters (DT-U.4).
 */
object KeyboardFontScale {
    const val MAX_FONT_SCALE: Float = 1.0f

    fun cap(fontScale: Float): Float = fontScale.coerceAtMost(MAX_FONT_SCALE)

    fun capped(density: Density): Density {
        val capped = cap(density.fontScale)
        return if (capped == density.fontScale) density else Density(density.density, capped)
    }
}
