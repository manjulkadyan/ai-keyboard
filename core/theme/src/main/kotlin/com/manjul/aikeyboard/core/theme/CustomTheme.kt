/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.theme

import kotlin.math.roundToLong

/**
 * The "Custom colours" theme (S5 Task 8, PRD FR-18). The user picks 4 [Element] colours; pressed key and popup are
 * derived. It is free (decision 17) and lives in its own extension [EXTENSION_ID], so `PremiumThemeGuard`, which only
 * looks at [SnyggGenerator.EXTENSION_ID], never touches it.
 */
object CustomTheme {
    const val EXTENSION_ID: String = "com.manjul.aikeyboard.themes.custom"
    const val STYLESHEET_ID: String = "custom"

    enum class Element { KEYS, KEY_TEXT, BACKGROUND, ACCENT }

    /** The start point when nothing is saved: the `light` preset. */
    val DEFAULT_BASE: ThemeSpec = ThemePresets.all.first { it.id == "light" }.spec

    /** Relative luminance of the key colour above which the pressed key is darker, else lighter. */
    private const val LIGHT_KEY_LUMINANCE = 0.5

    private const val PRESSED_SHIFT = 0.12
    private const val POPUP_SHIFT = 0.06

    /** [base] with the chosen [colors]; a missing element keeps its [base] colour. Never dynamic. */
    fun spec(colors: Map<Element, Long>, base: ThemeSpec): ThemeSpec {
        val key = colors[Element.KEYS] ?: base.colors.key
        val light = relativeLuminance(key) > LIGHT_KEY_LUMINANCE
        val toward = if (light) BLACK else WHITE
        return base.copy(
            colors = ThemeColors(
                key = key,
                keyPressed = mix(key, toward, PRESSED_SHIFT),
                keyboardBackground = colors[Element.BACKGROUND] ?: base.colors.keyboardBackground,
                text = colors[Element.KEY_TEXT] ?: base.colors.text,
                accent = colors[Element.ACCENT] ?: base.colors.accent,
                popup = mix(key, toward, POPUP_SHIFT),
            ),
            dynamic = null,
        )
    }

    /** The colours of [base] for every element. */
    fun defaults(base: ThemeSpec): Map<Element, Long> = mapOf(
        Element.KEYS to base.colors.key,
        Element.KEY_TEXT to base.colors.text,
        Element.BACKGROUND to base.colors.keyboardBackground,
        Element.ACCENT to base.colors.accent,
    )

    /** The `FeatureSettings.customThemeColors` form: one ARGB value per [Element], in declaration order. */
    fun toStored(colors: Map<Element, Long>): List<Long> = Element.entries.map { requireNotNull(colors[it]) }

    /** The colours of a stored list, or [defaults] of [base] when the list has the wrong size or a bad value. */
    fun fromStored(stored: List<Long>, base: ThemeSpec): Map<Element, Long> {
        if (stored.size != Element.entries.size || stored.any { it !in ThemeColors.ARGB_RANGE }) return defaults(base)
        return Element.entries.zip(stored).toMap()
    }

    private const val BLACK = 0xFF000000L
    private const val WHITE = 0xFFFFFFFFL

    /** (1 - [weight]) of [a] plus [weight] of [b] per channel; the alpha of [a] is kept. */
    private fun mix(a: Long, b: Long, weight: Double): Long {
        var out = a and 0xFF000000L
        for (shift in listOf(16, 8, 0)) {
            val ca = ((a shr shift) and 0xFF).toDouble()
            val cb = ((b shr shift) and 0xFF).toDouble()
            out = out or ((ca * (1 - weight) + cb * weight).roundToLong().coerceIn(0L, 255L) shl shift)
        }
        return out
    }
}
