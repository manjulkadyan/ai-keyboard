/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.suggest

import kotlin.test.Test
import kotlin.test.assertEquals

class KeyProximityTest {
    private val qwerty = KeyProximity.QWERTY

    @Test fun sameRowNeighboursAreAdjacent() {
        assertEquals(0.5, qwerty.substitutionCost('q', 'w'))
        assertEquals(0.5, qwerty.substitutionCost('w', 'e'))
        assertEquals(0.5, qwerty.substitutionCost('n', 'm'))
    }

    @Test fun staggeredRowNeighboursAreAdjacent() {
        assertEquals(0.5, qwerty.substitutionCost('a', 'q'))
        assertEquals(0.5, qwerty.substitutionCost('a', 'w'))
        assertEquals(0.5, qwerty.substitutionCost('z', 'a'))
        assertEquals(0.5, qwerty.substitutionCost('z', 's'))
        assertEquals(0.5, qwerty.substitutionCost('m', 'j'))
        assertEquals(0.5, qwerty.substitutionCost('m', 'k'))
    }

    @Test fun distantKeysCostOne() {
        assertEquals(1.0, qwerty.substitutionCost('q', 'p'))
        assertEquals(1.0, qwerty.substitutionCost('q', 's'))
        assertEquals(1.0, qwerty.substitutionCost('a', 'm'))
    }

    @Test fun caseInsensitive() {
        assertEquals(0.0, qwerty.substitutionCost('A', 'a'))
        assertEquals(0.5, qwerty.substitutionCost('Q', 'w'))
        assertEquals(0.0, qwerty.substitutionCost('x', 'x'))
    }

    @Test fun unknownCharactersCostOne() {
        assertEquals(1.0, qwerty.substitutionCost('é', 'e'))
        assertEquals(1.0, qwerty.substitutionCost('1', '2'))
    }

    @Test fun latinLocalesUseQwerty() {
        for (locale in listOf("en", "en-US", "es", "fr_FR", "pt-BR", "id")) {
            assertEquals(0.5, KeyProximity.forLocale(locale).substitutionCost('q', 'w'), locale)
        }
    }

    @Test fun shippedNonLatinLayoutsHaveProximity() {
        // Arabic row 1 starts ض ص, Urdu row 1 starts ض ص, Hindi phonetic row 1 starts ट व.
        assertEquals(0.5, KeyProximity.forLocale("ar").substitutionCost('ض', 'ص'))
        assertEquals(0.5, KeyProximity.forLocale("ur").substitutionCost('ض', 'ص'))
        assertEquals(0.5, KeyProximity.forLocale("hi").substitutionCost('ट', 'व'))
        assertEquals(1.0, KeyProximity.forLocale("ar").substitutionCost('ض', 'ج'))
    }

    @Test fun unknownLocaleIsUniform() {
        val uniform = KeyProximity.forLocale("xx")
        assertEquals(1.0, uniform.substitutionCost('q', 'w'))
        assertEquals(0.0, uniform.substitutionCost('q', 'Q'))
    }
}
