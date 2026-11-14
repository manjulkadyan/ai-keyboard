/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.clipboard

enum class SmartChipKind { EMAIL, URL, PHONE }

data class SmartChip(val kind: SmartChipKind, val value: String)

/**
 * Email, link and phone chips for a clipboard text item. Found at render time with JVM regexes and never stored.
 * The values are user content: never log them or send them to analytics.
 */
object SmartChips {
    private val Email =
        Regex("""(?<![\w.%+-])[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}(?![\w-])""")
    private val Url = Regex("""(?i)\b(?:https?://|www\.)[^\s<>"]+""")
    private val Phone = Regex("""(?<![\w+])\+?(?:\(\d{1,4}\)[ .-]?)?\d(?:[ .-]?\d)+(?!\w)""")
    private val DateLike = Regex("""\d{1,4}[./-]\d{1,2}[./-]\d{1,4}""")
    private const val URL_TRAILING = ".,;:!?)]}'\""
    private const val MIN_URL_LENGTH = 5
    private const val MIN_PHONE_DIGITS = 7
    private const val MAX_PHONE_DIGITS = 15

    /** No chip on a masked (sensitive) item or an item without text. */
    fun forItem(text: String?, masked: Boolean, max: Int = 3): List<SmartChip> =
        if (masked || text.isNullOrBlank()) emptyList() else extract(text, max)

    fun extract(text: String, max: Int = 3): List<SmartChip> {
        if (max <= 0) return emptyList()
        val found = ArrayList<Pair<Int, SmartChip>>()
        val taken = ArrayList<IntRange>()
        for (match in Url.findAll(text)) {
            val value = match.value.trimEnd { it in URL_TRAILING }
            if (value.length < MIN_URL_LENGTH) continue
            found += match.range.first to SmartChip(SmartChipKind.URL, value)
            taken += match.range
        }
        for (match in Email.findAll(text)) {
            if (taken.any { match.range.first in it }) continue
            found += match.range.first to SmartChip(SmartChipKind.EMAIL, match.value)
            taken += match.range
        }
        for (match in Phone.findAll(text)) {
            if (taken.any { match.range.first in it || match.range.last in it }) continue
            val digits = match.value.count { it.isDigit() }
            if (digits !in MIN_PHONE_DIGITS..MAX_PHONE_DIGITS || DateLike.matches(match.value)) continue
            found += match.range.first to SmartChip(SmartChipKind.PHONE, match.value)
        }
        return found.sortedBy { it.first }.map { it.second }.distinct().take(max)
    }
}
