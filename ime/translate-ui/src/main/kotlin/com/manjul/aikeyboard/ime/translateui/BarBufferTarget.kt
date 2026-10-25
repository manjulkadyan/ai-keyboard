/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.translateui

import com.manjul.aikeyboard.ime.editing.TextTarget

/**
 * A [TextTarget] over the bar buffer, so voice partials compose into the bar instead of the host while the bar is
 * open. The composing region is the tail of the buffer this target wrote; it stays ours only while the buffer still
 * ends with exactly that text (a key typed after it, or [BarBuffer.clear], ends it). Text is user content: never
 * logged.
 */
class BarBufferTarget(private val buffer: BarBuffer) : TextTarget {
    private var regionStart = NO_REGION
    private var region = ""

    override fun setComposing(text: String): Boolean {
        val base = textBeforeRegion()
        buffer.replace(base + text)
        val written = buffer.text.value
        // A too-long result is cut at a grapheme boundary, which may fall inside the base.
        regionStart = minOf(base.length, written.length)
        region = written.substring(regionStart)
        return true
    }

    override fun commit(text: String, removeSpaceBefore: Boolean): Boolean {
        var base = textBeforeRegion()
        if (removeSpaceBefore && base.endsWith(' ')) base = base.dropLast(1)
        buffer.replace(base + text)
        forgetRegion()
        return true
    }

    override fun finishComposing(): Boolean {
        forgetRegion()
        return true
    }

    override fun clearComposing(expected: String): Boolean {
        val current = buffer.text.value
        val removed = regionIsOurs(current) && region == expected
        if (removed) buffer.replace(current.substring(0, regionStart))
        forgetRegion()
        return removed
    }

    override fun deleteBeforeCursorIfEquals(expected: String): Boolean {
        val current = buffer.text.value
        if (expected.isEmpty() || !current.endsWith(expected)) return false
        buffer.replace(current.dropLast(expected.length))
        forgetRegion()
        return true
    }

    /** The buffer without our composing region, or the whole buffer when there is none (a new one starts at the end). */
    private fun textBeforeRegion(): String {
        val current = buffer.text.value
        return if (regionIsOurs(current)) current.substring(0, regionStart) else current
    }

    private fun regionIsOurs(current: String): Boolean =
        regionStart != NO_REGION && regionStart + region.length == current.length &&
            current.regionMatches(regionStart, region, 0, region.length)

    private fun forgetRegion() {
        regionStart = NO_REGION
        region = ""
    }

    override fun toString(): String = "BarBufferTarget(composing=${regionStart != NO_REGION}, chars=${region.length})"

    private companion object {
        const val NO_REGION = -1
    }
}
