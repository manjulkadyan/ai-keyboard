/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate

import kotlinx.coroutines.CancellationException
import java.util.Locale

/**
 * Picks the source language of a request (PRD FR-11 AC2, plan decision 3). The text is user content, never
 * logged; it is only handed to the [detector].
 *
 * An explicit source always wins. Otherwise the top detector result is used when its confidence is at least
 * [threshold], it is not `und`, and it is not a romanised (`*-Latn`) result for a language whose primary
 * script is not Latin: ML Kit reports Hinglish as `hi-Latn`, and translating that as Devanagari Hindi gives
 * garbage. In every other case the user's last source language is kept.
 */
class SourceResolver(
    private val detector: LanguageDetector,
    private val threshold: Float = DEFAULT_THRESHOLD,
) {
    suspend fun resolve(text: String, requested: Lang?, lastSource: Lang): Lang {
        if (requested != null) return requested
        if (text.isBlank()) return lastSource
        val candidates = try {
            detector.identify(text)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Message dropped: a detector error may quote the text.
            return lastSource
        }
        val (tag, confidence) = candidates.maxByOrNull { it.second } ?: return lastSource
        if (confidence < threshold) return lastSource
        return trusted(tag) ?: lastSource
    }

    /** The language of a detector tag, or `null` when the tag must not be trusted. */
    private fun trusted(tag: String): Lang? {
        val parts = tag.trim().split('-', '_')
        val lang = Lang.of(parts.first())
        if (lang.tag.isEmpty() || lang.tag == UNDETERMINED) return null
        val romanised = parts.drop(1).any { it.lowercase(Locale.ROOT) == "latn" }
        if (romanised && lang.tag in NON_LATIN) return null
        return lang
    }

    companion object {
        const val DEFAULT_THRESHOLD = 0.5f
        private const val UNDETERMINED = "und"

        /** Languages whose primary script is not Latin; a `-Latn` detection of these is romanised text. */
        val NON_LATIN: Set<String> = setOf(
            "ar", "ur", "hi", "bn", "fa", "ps", "ru", "uk", "el", "he", "ja", "zh", "ko", "th", "ta", "te", "mr",
            "gu", "kn", "ml", "pa",
        )
    }
}
