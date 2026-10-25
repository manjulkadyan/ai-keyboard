/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate

import com.manjul.aikeyboard.core.translate.testing.FakeLanguageDetector
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SourceResolverTest {
    private val last = Lang("en")

    private suspend fun resolve(vararg candidates: Pair<String, Float>, requested: Lang? = null, text: String = "text"): Lang =
        SourceResolver(FakeLanguageDetector(mapOf("text" to candidates.toList()))).resolve(text, requested, last)

    @Test fun explicitSourceWins() = runTest {
        assertEquals(Lang("fr"), resolve("ur" to 0.9f, requested = Lang("fr")))
    }

    @Test fun confidentResultIsUsed() = runTest {
        assertEquals(Lang("ur"), resolve("ur" to 0.9f))
        assertEquals(Lang("ur"), resolve("hi" to 0.2f, "ur" to 0.5f))
    }

    @Test fun lowConfidenceKeepsLastSource() = runTest {
        assertEquals(last, resolve("ur" to 0.4f))
    }

    @Test fun romanisedNonLatinLanguageKeepsLastSource() = runTest {
        assertEquals(last, resolve("hi-Latn" to 0.95f))
        assertEquals(last, resolve("ur-Latn" to 0.95f))
    }

    @Test fun latinScriptLanguageWithLatnTagIsTrusted() = runTest {
        assertEquals(Lang("es"), resolve("es-Latn" to 0.9f))
    }

    @Test fun undeterminedKeepsLastSource() = runTest {
        assertEquals(last, resolve("und" to 1.0f))
    }

    @Test fun emptyTextOrNoCandidatesKeepsLastSource() = runTest {
        assertEquals(last, resolve("ur" to 0.9f, text = "  "))
        assertEquals(last, resolve())
    }

    @Test fun detectorErrorKeepsLastSource() = runTest {
        val failing = object : LanguageDetector {
            override suspend fun identify(text: String): List<Pair<String, Float>> = error("boom")
        }
        assertEquals(last, SourceResolver(failing).resolve("text", null, last))
    }

    @Test fun langNormalisesToPrimarySubtag() {
        assertEquals(Lang("ur"), Lang.of("ur-PK"))
        assertEquals(Lang("pt"), Lang.of("PT_br"))
        assertEquals(LangPair(Lang("es"), Lang("en")), LangPair(Lang("en"), Lang("es")).swapped())
    }
}
