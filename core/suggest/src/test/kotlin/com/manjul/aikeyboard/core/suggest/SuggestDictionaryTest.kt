/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.suggest

import com.manjul.aikeyboard.core.suggest.testing.TestDictionaries
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SuggestDictionaryTest {
    private val dict = TestDictionaries.en()

    @Test fun hasPrefixFindsWholeWordsAndProperPrefixes() {
        assertTrue(dict.hasPrefix("the"))
        assertTrue(dict.hasPrefix("th"))
        assertTrue(dict.hasPrefix("hel"))
        assertTrue(dict.hasPrefix("w"))
    }

    @Test fun hasPrefixRejectsMissingPrefixes() {
        assertFalse(dict.hasPrefix("thx"))
        assertFalse(dict.hasPrefix("zz"))
        assertFalse(dict.hasPrefix("worlds"))
        // Past the last word in sort order.
        assertFalse(dict.hasPrefix("zzz"))
    }

    @Test fun hasPrefixFallsBackToLowerCaseLikeLookups() {
        assertTrue(dict.hasPrefix("Th"))
        assertFalse(dict.hasPrefix("Zz"))
    }

    @Test fun emptyPrefixMatchesOnlyANonEmptyDictionary() {
        assertTrue(dict.hasPrefix(""))
        assertFalse(TestDictionaries.build("en", emptyList()).hasPrefix(""))
    }

    @Test fun hasPrefixWorksOnNonLatinScripts() {
        val hi = TestDictionaries.build("hi", listOf("है" to 10, "हैं" to 5, "खाना" to 3))
        assertTrue(hi.hasPrefix("खा"))
        assertTrue(hi.hasPrefix("है"))
        assertFalse(hi.hasPrefix("खी"))
    }
}
