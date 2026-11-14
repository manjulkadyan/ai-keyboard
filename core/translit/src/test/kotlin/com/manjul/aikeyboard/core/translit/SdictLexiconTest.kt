/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translit

import com.manjul.aikeyboard.core.suggest.DictionaryFormat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SdictLexiconTest {
    @Test fun readsTheShippedHindiDictionary() {
        val lexicon = TestLexicons.shipped(TranslitLang.HI)
        assertNotNull(lexicon.frequency("है"))
        assertTrue(lexicon.prefixExists("है"))
        assertTrue(lexicon.prefixExists("खा"))
        assertFalse(lexicon.prefixExists("ॐॐॐ"))
    }

    @Test fun readsTheShippedUrduDictionary() {
        val lexicon = TestLexicons.shipped(TranslitLang.UR)
        assertEquals(16, lexicon.frequency("ہے"))
        assertTrue(lexicon.prefixExists("کھا"))
    }

    @Test fun unknownWordIsNullNotZero() {
        val out = ByteArrayOutputStream()
        DictionaryFormat.writeScaled(out, "hi", listOf("है" to 200, "हैं" to 1))
        val lexicon = SdictLexicon.read(ByteArrayInputStream(out.toByteArray()))
        assertEquals(200, lexicon.frequency("है"))
        assertEquals(1, lexicon.frequency("हैं"))
        assertNull(lexicon.frequency("ह"))
        assertNull(lexicon.frequency(""))
        assertTrue(lexicon.prefixExists("ह"))
        assertTrue(lexicon.prefixExists(""))
        assertFalse(lexicon.prefixExists("हो"))
        assertEquals("SdictLexicon(size=2)", lexicon.toString())
    }
}
