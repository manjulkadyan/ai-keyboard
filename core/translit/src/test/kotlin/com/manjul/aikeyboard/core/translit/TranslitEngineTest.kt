/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TranslitEngineTest {
    private fun engine(lang: TranslitLang, mappings: CustomMappings = CustomMappings()) =
        TranslitEngine(lang, RuleTable.load(lang), TestLexicons.mini(lang), mappings)

    @Test fun suggestReturnsAtMostMax() {
        val out = engine(TranslitLang.UR).suggest("kya", 3)
        assertTrue(out.size in 1..3)
        assertEquals("کیا", out.first().native) // کیا
        assertEquals(Source.LEXICON, out.first().source)
        assertTrue(engine(TranslitLang.HI).suggest("kya", 1).size == 1)
        assertTrue(engine(TranslitLang.HI).suggest("kya", 0).isEmpty())
    }

    @Test fun mappingIsFirst() {
        val mappings = CustomMappings()
        mappings.put("kya", "کیاں")
        val out = engine(TranslitLang.UR, mappings).suggest("Kya", 3)
        assertEquals("کیاں", out.first().native)
        assertEquals(Source.MAPPING, out.first().source)
        assertTrue(out.size <= 3)
        assertEquals(out.size, out.map { it.native }.toSet().size)
    }

    @Test fun mappingSurvivesAnInputTheDecoderRejects() {
        val mappings = CustomMappings()
        val long = "a".repeat(30)
        mappings.put(long, "x")
        val out = engine(TranslitLang.HI, mappings).suggest(long, 3)
        assertEquals(listOf(Source.MAPPING), out.map { it.source })
    }

    @Test fun boostIsPassedToTheRanker() {
        val e = engine(TranslitLang.HI)
        val plain = e.suggest("zorbify", 3)
        assertTrue(plain.size >= 2)
        val last = plain.last().native
        val boosted = e.suggest("zorbify", 3) { if (it == last) 100 else 0 }
        assertEquals(last, boosted.first().native)
    }

    @Test fun toStringHoldsNoText() {
        assertEquals("TranslitEngine(lang=ur)", engine(TranslitLang.UR).toString())
    }
}
