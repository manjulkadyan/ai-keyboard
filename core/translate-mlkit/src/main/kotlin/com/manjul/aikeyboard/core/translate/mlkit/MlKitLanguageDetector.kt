/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate.mlkit

import android.content.Context
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.manjul.aikeyboard.core.translate.LanguageDetector
import kotlinx.coroutines.tasks.await

/**
 * ML Kit Language ID behind [LanguageDetector]. The client is created on the first [identify] (which also
 * initialises ML Kit) and released by [close]. Only the first [MAX_CHARS] characters are identified. Text is
 * user content, never logged; errors propagate without being logged and [com.manjul.aikeyboard.core.translate.SourceResolver]
 * drops them.
 */
class MlKitLanguageDetector(private val context: Context) : LanguageDetector, AutoCloseable {
    @Volatile private var client: LanguageIdentifier? = null

    override suspend fun identify(text: String): List<Pair<String, Float>> {
        val head = cap(text)
        if (head.isBlank()) return emptyList()
        return client().identifyPossibleLanguages(head).await().map { it.languageTag to it.confidence }
    }

    override fun close() {
        synchronized(this) {
            client?.close()
            client = null
        }
    }

    private fun client(): LanguageIdentifier = client ?: synchronized(this) {
        client ?: run {
            MlKitBootstrap.ensure(context)
            LanguageIdentification.getClient(
                LanguageIdentificationOptions.Builder().setConfidenceThreshold(CONFIDENCE_FLOOR).build(),
            ).also { client = it }
        }
    }

    companion object {
        const val MAX_CHARS = 200

        /** Report nearly everything; [com.manjul.aikeyboard.core.translate.SourceResolver] applies the real 0.5 cut. */
        private const val CONFIDENCE_FLOOR = 0.01f

        /** The first [MAX_CHARS] chars, without splitting a surrogate pair. */
        internal fun cap(text: String): String {
            if (text.length <= MAX_CHARS) return text
            val end = if (Character.isHighSurrogate(text[MAX_CHARS - 1])) MAX_CHARS - 1 else MAX_CHARS
            return text.substring(0, end)
        }
    }
}
