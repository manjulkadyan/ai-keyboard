/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.theme

/** ARGB colours of a theme, each in `0..0xFFFFFFFF`. [text] is drawn on [key], [keyboardBackground] and [popup]. */
data class ThemeColors(
    val key: Long,
    val keyPressed: Long,
    val keyboardBackground: Long,
    val text: Long,
    val accent: Long,
    val popup: Long,
) {
    init {
        for ((name, value) in listOf(
            "key" to key, "keyPressed" to keyPressed, "keyboardBackground" to keyboardBackground,
            "text" to text, "accent" to accent, "popup" to popup,
        )) {
            require(value in ARGB_RANGE) { "$name is not an ARGB value" }
        }
    }

    companion object {
        val ARGB_RANGE: LongRange = 0L..0xFFFFFFFFL
    }
}

/** Plan decision 7: only [COLOR] is generated in S4. [GRADIENT] and [IMAGE] are v1. */
enum class BackgroundType { COLOR, GRADIENT, IMAGE }

/**
 * The keyboard background. For [BackgroundType.COLOR] the colour is [ThemeColors.keyboardBackground] and [value] is
 * unused. [value] is reserved for a v1 gradient spec or image path, and [blurRadius] for v1 images.
 */
data class Background(
    val type: BackgroundType,
    val value: String,
    val opacity: Float,
    val blurRadius: Int,
) {
    init {
        require(opacity in 0f..1f) { "opacity must be in 0..1" }
        require(blurRadius in 0..MAX_BLUR_RADIUS) { "blurRadius must be in 0..$MAX_BLUR_RADIUS" }
    }

    companion object {
        const val MAX_BLUR_RADIUS: Int = 25
        val SOLID: Background = Background(BackgroundType.COLOR, value = "", opacity = 1f, blurRadius = 0)
    }
}

/** Key label font. [ROUNDED] needs a bundled font and is not generated in S4. */
enum class ThemeFont { DEFAULT, ROUNDED, MONO }

/** Material You: the colours come from the system palette (API 31+) instead of [ThemeSpec.colors]. */
enum class DynamicScheme { LIGHT, DARK }

/**
 * One theme. [cornerRadiusDp] is the key corner radius. [keyGapDp] is extra space between keys on top of the
 * keyboard spacing preference. When [dynamic] is set, [colors] are only the preview fallback.
 */
data class ThemeSpec(
    val colors: ThemeColors,
    val cornerRadiusDp: Int,
    val keyGapDp: Int,
    val font: ThemeFont,
    val background: Background,
    val dynamic: DynamicScheme?,
) {
    init {
        require(cornerRadiusDp >= 0) { "cornerRadiusDp must not be negative" }
        require(keyGapDp >= 0) { "keyGapDp must not be negative" }
    }
}

/** A shipped preset. [id] is the stylesheet id inside the `com.manjul.aikeyboard.themes` extension. */
data class ThemePreset(
    val id: String,
    val spec: ThemeSpec,
    val premium: Boolean,
    val minSdk: Int,
)
