/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.theme

/**
 * The 12 shipped presets (plan Task 9, decision 9). Every colour is an original choice of the AI Keyboard
 * contributors. Text on key keeps a WCAG contrast of at least 4.5 (`ThemePresetsTest`). The Material You presets
 * take their colours from the system palette; their [ThemeColors] are only the preview fallback.
 */
object ThemePresets {
    private const val MIN_SDK = 26
    private const val MATERIAL_YOU_MIN_SDK = 31

    private val lightColors = ThemeColors(
        key = 0xFFFDFDFE, keyPressed = 0xFFE9ECF1, keyboardBackground = 0xFFDCDFE4,
        text = 0xFF1B1D21, accent = 0xFF2F6FDB, popup = 0xFFF3F4F7,
    )
    private val darkColors = ThemeColors(
        key = 0xFF33363C, keyPressed = 0xFF45494F, keyboardBackground = 0xFF1C1E22,
        text = 0xFFF1F2F4, accent = 0xFF6FA0F5, popup = 0xFF2A2D32,
    )

    private fun preset(
        id: String,
        colors: ThemeColors,
        premium: Boolean = false,
        cornerRadiusDp: Int = 7,
        dynamic: DynamicScheme? = null,
    ) = ThemePreset(
        id = id,
        spec = ThemeSpec(
            colors = colors,
            cornerRadiusDp = cornerRadiusDp,
            keyGapDp = 0,
            font = ThemeFont.DEFAULT,
            background = Background.SOLID,
            dynamic = dynamic,
        ),
        premium = premium,
        minSdk = if (dynamic != null) MATERIAL_YOU_MIN_SDK else MIN_SDK,
    )

    val all: List<ThemePreset> = listOf(
        preset("light", lightColors),
        preset("dark", darkColors),
        preset(
            "amoled",
            ThemeColors(
                key = 0xFF141414, keyPressed = 0xFF262626, keyboardBackground = 0xFF000000,
                text = 0xFFEDEDED, accent = 0xFF3FD0A4, popup = 0xFF1A1A1A,
            ),
        ),
        preset("material_you_light", lightColors, cornerRadiusDp = 10, dynamic = DynamicScheme.LIGHT),
        preset("material_you_dark", darkColors, cornerRadiusDp = 10, dynamic = DynamicScheme.DARK),
        preset(
            "ocean",
            ThemeColors(
                key = 0xFF0F4C5C, keyPressed = 0xFF16687C, keyboardBackground = 0xFF0A3440,
                text = 0xFFE6F6F8, accent = 0xFF4FD1C5, popup = 0xFF124554,
            ),
        ),
        preset(
            "forest",
            ThemeColors(
                key = 0xFF2C4033, keyPressed = 0xFF3B5544, keyboardBackground = 0xFF1D2B22,
                text = 0xFFEAF2E6, accent = 0xFF9CCB6B, popup = 0xFF263A2D,
            ),
        ),
        preset(
            "slate",
            ThemeColors(
                key = 0xFF4D5766, keyPressed = 0xFF5E6979, keyboardBackground = 0xFF3A4350,
                text = 0xFFF4F6F9, accent = 0xFFF2B84B, popup = 0xFF465060,
            ),
        ),
        preset(
            "sunset",
            ThemeColors(
                key = 0xFFFFF4EC, keyPressed = 0xFFF9E2D2, keyboardBackground = 0xFFF7D9C4,
                text = 0xFF4A1F12, accent = 0xFFE2683C, popup = 0xFFFFEADB,
            ),
            premium = true,
        ),
        preset(
            "rose",
            ThemeColors(
                key = 0xFFFFF5F8, keyPressed = 0xFFF8E1E8, keyboardBackground = 0xFFF3D6DE,
                text = 0xFF4B1528, accent = 0xFFC2416B, popup = 0xFFFCE8EE,
            ),
            premium = true,
        ),
        preset(
            "midnight",
            ThemeColors(
                key = 0xFF1C2350, keyPressed = 0xFF2A3266, keyboardBackground = 0xFF0E1330,
                text = 0xFFE4E8FF, accent = 0xFF8C7CF0, popup = 0xFF171D42,
            ),
            premium = true,
        ),
        preset(
            "sand",
            ThemeColors(
                key = 0xFFF7F0E1, keyPressed = 0xFFEDE2CB, keyboardBackground = 0xFFE8DCC4,
                text = 0xFF3B3022, accent = 0xFFB07A34, popup = 0xFFF1E7D3,
            ),
            premium = true,
            cornerRadiusDp = 12,
        ),
    )
}
