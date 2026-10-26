/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.suggest

import com.manjul.aikeyboard.core.suggest.testing.InMemoryLearnedWords
import com.manjul.aikeyboard.core.suggest.testing.TestDictionaries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SuggestEngineTest {
    private val learned = InMemoryLearnedWords()
    private fun engine(dict: SuggestDictionary = TestDictionaries.en()) = SuggestEngine(dict, KeyProximity.QWERTY, learned)

    @Test fun transpositionIsCorrected() {
        val top = engine().suggest("teh", null)[0]
        assertEquals("the", top.word)
        assertFalse(top.isTypedWord)
    }

    @Test fun capitalisedInputKeepsCase() {
        assertEquals("The", engine().suggest("Teh", null)[0].word)
    }

    @Test fun allCapsInputKeepsCase() {
        assertEquals("THE", engine().suggest("TEH", null)[0].word)
    }

    @Test fun prefixCompletions() {
        val words = engine().suggest("hel", null).map { it.word }
        assertTrue("hello" in words)
        assertTrue("help" in words)
    }

    @Test fun learnedBoostLiftsRareWordAboveCommonOne() {
        val before = engine().suggest("hel", null).map { it.word }
        assertTrue(before.indexOf("helm") == -1 || before.indexOf("helm") > before.indexOf("hello"))
        learned.learn("en", "helm", 5)
        val after = engine().suggest("hel", null)
        assertEquals("helm", after[0].word)
        assertTrue(after[0].isExactOrLearned)
    }

    @Test fun blockedWordIsAbsent() {
        learned.block("en", "hello")
        val words = engine().suggest("hel", null).map { it.word } + engine().suggest("helo", null).map { it.word }
        assertFalse("hello" in words)
    }

    @Test fun blockedTypedWordIsAbsent() {
        learned.block("en", "the")
        assertFalse(engine().suggest("the", null).any { it.word.equals("the", ignoreCase = true) })
    }

    @Test fun aBlockedDictionaryWordIsStillKnown() {
        learned.block("en", "the")
        assertTrue(engine().isKnown("the"))
        assertTrue(engine().isKnown("The"))
        assertFalse(engine().isKnown("teh"))
        learned.learn("en", "manjul", 1)
        assertTrue(engine().isKnown("Manjul"))
    }

    @Test fun maxIsHonoured() {
        assertEquals(1, engine().suggest("the", null, max = 1).size)
        assertTrue(engine().suggest("he", null, max = 2).size <= 2)
        assertTrue(engine().suggest("the", null, max = 0).isEmpty())
    }

    @Test fun typedDictionaryWordComesFirst() {
        val result = engine().suggest("then", null)
        assertEquals("then", result[0].word)
        assertTrue(result[0].isTypedWord)
        assertTrue(result[0].isExactOrLearned)
    }

    @Test fun typedLearnedWordComesFirst() {
        learned.learn("en", "manjul", 2)
        val result = engine().suggest("Manjul", null)
        assertEquals("Manjul", result[0].word)
        assertTrue(result[0].isTypedWord)
    }

    @Test fun unknownTypedWordIsNotACandidate() {
        assertTrue(engine().suggest("teh", null).none { it.isTypedWord })
    }

    @Test fun scoresAreInUnitRangeAndSorted() {
        learned.learn("en", "helm", 50)
        learned.learnBigram("en", "say", "hello", 50)
        val result = engine().suggest("hel", "say", max = 10)
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it.score in 0.0..1.0 })
        val nonTyped = result.filterNot { it.isTypedWord }
        assertEquals(nonTyped.sortedByDescending { it.score }.map { it.score }, nonTyped.map { it.score })
    }

    @Test fun bigramRaisesFollowingWord() {
        val dict = TestDictionaries.en(bigrams = listOf(Triple("say", "help", 1_000)))
        assertEquals("hello", engine(dict).suggest("hel", null)[0].word)
        assertEquals("help", engine(dict).suggest("hel", "say")[0].word)
        assertEquals("help", engine(dict).suggest("hel", "Say")[0].word)
    }

    @Test fun learnedBigramRaisesFollowingWord() {
        learned.learnBigram("en", "say", "help", 10)
        assertEquals("help", engine().suggest("hel", "say")[0].word)
    }

    @Test fun commonCorrectionScoresHighEnoughToAutocorrect() {
        assertTrue(engine().suggest("teh", null)[0].score >= 0.7)
    }

    @Test fun adjacentKeySlipIsCorrected() {
        assertEquals("hello", engine().suggest("hwllo", null)[0].word)
    }

    @Test fun singleCharacterPrefersCorrectionsTierThenCompletions() {
        val result = engine().suggest("t", null)
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it.word.startsWith("t") })
    }

    @Test fun blankInputHasNoCandidates() {
        assertTrue(engine().suggest("", null).isEmpty())
        assertTrue(engine().suggest("   ", null).isEmpty())
    }

    @Test fun noDuplicateWords() {
        val words = engine().suggest("the", null, max = 10).map { it.word.lowercase() }
        assertEquals(words.distinct(), words)
    }

    @Test fun swappedFirstLettersOnLongWordAreCorrected() {
        assertEquals("hello", engine().suggest("ehllo", null)[0].word)
    }

    @Test fun adjacentFirstLetterOnLongWordIsCorrected() {
        assertEquals("world", engine().suggest("qorld", null)[0].word)
    }

    @Test fun candidatesNearFindsWholeWordsAndCompletions() {
        val near = TestDictionaries.en().candidatesNear("hel", 1).toList()
        assertTrue("hello" in near && "help" in near && "helm" in near)
        assertFalse("world" in near)
    }
}
