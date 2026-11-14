/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BeamDecoderTest {
    private val ur = BeamDecoder(RuleTable.load(TranslitLang.UR), TestLexicons.mini(TranslitLang.UR))
    private val hi = BeamDecoder(RuleTable.load(TranslitLang.HI), TestLexicons.mini(TranslitLang.HI))

    private fun BeamDecoder.top(roman: String, n: Int = 3) = decode(roman).take(n).map { it.native }

    @Test fun khanaIsInTheTopThree() {
        assertTrue("کھانا" in ur.top("khana")) // کھانا
        assertTrue("खाना" in hi.top("khana")) // खाना
    }

    @Test fun haiIsInTheTopThree() {
        assertTrue("ہے" in ur.top("hai")) // ہے
        assertTrue("है" in hi.top("hai")) // है
    }

    @Test fun upperCaseInputDecodesLikeLowerCase() {
        assertEquals(hi.decode("khana").map { it.native }, hi.decode("KHANA").map { it.native })
    }

    @Test fun unknownWordStillGivesACandidate() {
        assertTrue(ur.decode("zorbify").isNotEmpty())
        assertTrue(hi.decode("zorbify").isNotEmpty())
    }

    @Test fun inputLongerThan24LettersGivesNothing() {
        assertTrue(hi.decode("a".repeat(25)).isEmpty())
        assertTrue(hi.decode("ka".repeat(12)).isNotEmpty())
        assertTrue(hi.decode("").isEmpty())
    }

    @Test fun inputWithoutRulesGivesNothing() {
        assertTrue(hi.decode("k1").isEmpty())
    }

    @Test fun beamNeverHoldsMoreThanItsWidth() {
        // An empty lexicon prunes nothing, so every step has far more than 16 live hypotheses.
        val decoder = BeamDecoder(RuleTable.load(TranslitLang.UR), MapLexicon(emptyMap()))
        val sizes = ArrayList<Int>()
        decoder.beamObserver = { sizes += it }
        decoder.decode("aaeeiaooaaeiua")
        assertTrue(sizes.isNotEmpty())
        assertEquals(16, sizes.max())

        val narrow = BeamDecoder(RuleTable.load(TranslitLang.UR), MapLexicon(emptyMap()), beamWidth = 4)
        val narrowSizes = ArrayList<Int>()
        narrow.beamObserver = { narrowSizes += it }
        assertTrue(narrow.decode("aaeeiaooaaeiua").size <= 4)
        assertEquals(4, narrowSizes.max())
    }

    @Test fun resultsAreSortedByCostAndUnique() {
        val out = BeamDecoder(RuleTable.load(TranslitLang.HI), MapLexicon(emptyMap())).decode("kitna")
        assertEquals(out.map { it.cost }.sorted(), out.map { it.cost })
        assertEquals(out.size, out.map { it.native }.toSet().size)
    }

    // Inline tables below, so cost tuning in the shipped tables cannot break these.

    private fun inline(lang: TranslitLang, vararg rows: String) = RuleTable.parse(lang, rows.asSequence())

    @Test fun lexiconPrefixBeatsACheaperNonPrefix() {
        val table = inline(TranslitLang.UR, "k\tک\t0.0\tany", "k\tق\t1.0\tany", "a\tا\t0.0\tany")
        val decoder = BeamDecoder(table, MapLexicon(mapOf("قا" to 16)))
        assertEquals(listOf("قا"), decoder.decode("ka").map { it.native })
    }

    @Test fun hindiConsonantsThatMeetGetAVirama() {
        val table = inline(TranslitLang.HI, "k\tक\t0.0\tany", "t\tत\t0.0\tany")
        val natives = BeamDecoder(table, MapLexicon(emptyMap())).decode("kt").map { it.native }
        assertTrue("क्त" in natives) // क्त
        // Schwa deletion: the plain pair stays as a dearer reading.
        assertTrue("कत" in natives)
        assertEquals("क्त", natives.first())
    }

    @Test fun conjunctRowAndGenericViramaGiveOneOutput() {
        val table = inline(TranslitLang.HI, "k\tक\t0.0\tany", "kt\tक्त\t0.5\tany", "t\tत\t0.0\tany")
        val out = BeamDecoder(table, MapLexicon(emptyMap())).decode("kt")
        val conjunct = out.filter { it.native == "क्त" }
        assertEquals(1, conjunct.size)
        assertEquals(0.0, conjunct.single().cost) // the cheaper path wins
    }

    @Test fun aVowelBetweenConsonantsBlocksTheVirama() {
        val table = inline(TranslitLang.HI, "k\tक\t0.0\tany", "a\t\t0.0\tmid", "t\tत\t0.0\tany")
        val natives = BeamDecoder(table, MapLexicon(emptyMap())).decode("kat").map { it.native }
        assertEquals(listOf("कत"), natives)
    }

    @Test fun urduNeverGetsAVirama() {
        val table = inline(TranslitLang.UR, "k\tک\t0.0\tany", "t\tت\t0.0\tany")
        val natives = BeamDecoder(table, MapLexicon(emptyMap())).decode("kt").map { it.native }
        assertEquals(listOf("کت"), natives)
        assertFalse(natives.any { '्' in it })
    }

    @Test fun toStringHoldsNoText() {
        val h = hi.decode("khana").first()
        assertFalse(h.toString().contains(h.native))
        assertEquals("BeamDecoder(lang=hi, beamWidth=16)", hi.toString())
    }
}
