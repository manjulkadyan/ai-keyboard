/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.suggest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DamerauLevenshteinTest {
    @Test fun transpositionCostsOne() {
        assertEquals(1.0, DamerauLevenshtein.distance("teh", "the"))
    }

    @Test fun insertionCostsOne() {
        assertEquals(1.0, DamerauLevenshtein.distance("helo", "hello"))
    }

    @Test fun equalStringsCostZero() {
        assertEquals(0.0, DamerauLevenshtein.distance("hello", "hello"))
    }

    @Test fun distanceAboveMaxIsInfinite() {
        assertTrue(DamerauLevenshtein.distance("abc", "xyz", max = 2).isInfinite())
    }

    @Test fun lengthGapAboveMaxIsInfinite() {
        assertTrue(DamerauLevenshtein.distance("a", "abcdef", max = 2).isInfinite())
    }

    @Test fun proximityCostMakesAdjacentSubstitutionCheaper() {
        assertEquals(0.5, DamerauLevenshtein.distance("hwllo", "hello", cost = KeyProximity.QWERTY::substitutionCost))
    }

    @Test fun emptyStrings() {
        assertEquals(0.0, DamerauLevenshtein.distance("", ""))
        assertEquals(2.0, DamerauLevenshtein.distance("", "ab"))
    }
}
