/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.theme

/** The hex field of the colour picker (S5 Task 8): `#RRGGBB` or `#AARRGGBB`, upper or lower case. */
object HexColor {
    private val PATTERN = Regex("#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})")

    /** The ARGB value of [text], or null when it is not `#RRGGBB` (opaque) or `#AARRGGBB`. */
    fun parse(text: String): Long? {
        val match = PATTERN.matchEntire(text) ?: return null
        val digits = match.groupValues[1]
        val value = digits.toLong(16)
        return if (digits.length == 6) 0xFF000000L or value else value
    }

    /** `#RRGGBB`, or `#AARRGGBB` when [withAlpha]; upper case. */
    fun format(argb: Long, withAlpha: Boolean): String {
        val masked = if (withAlpha) argb and 0xFFFFFFFFL else argb and 0xFFFFFFL
        val width = if (withAlpha) 8 else 6
        return "#" + masked.toString(16).uppercase().padStart(width, '0')
    }
}
