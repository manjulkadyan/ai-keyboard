/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.suggest

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutocorrectPolicyTest {
    private val the = Candidate("the", 0.9, isExactOrLearned = false, isTypedWord = false)

    private fun decide(
        typed: String = "teh",
        top: Candidate? = the,
        minScore: Double = 0.7,
        localeEnabled: Boolean = true,
        fieldAllows: Boolean = true,
        recentlyReverted: Boolean = false,
    ) = AutocorrectPolicy.shouldAutocorrect(typed, top, minScore, localeEnabled, fieldAllows, recentlyReverted)

    @Test fun correctsACommonTypo() {
        assertTrue(AutocorrectPolicy.shouldAutocorrect("teh", the, 0.7, true, true, false))
    }

    @Test fun localeDisabled() = assertFalse(decide(localeEnabled = false))

    @Test fun fieldDisallows() = assertFalse(decide(fieldAllows = false))

    @Test fun recentlyReverted() = assertFalse(decide(recentlyReverted = true))

    @Test fun typedContainsDigit() = assertFalse(decide(typed = "te2h"))

    @Test fun typedContainsAt() = assertFalse(decide(typed = "te@h"))

    @Test fun typedContainsSlash() = assertFalse(decide(typed = "te/h"))

    @Test fun typedContainsDotInsideTheWord() = assertFalse(decide(typed = "te.h"))

    @Test fun typedContainsLeadingDot() = assertFalse(decide(typed = ".teh"))

    @Test fun typedContainsUnderscore() = assertFalse(decide(typed = "te_h"))

    @Test fun typedTooShort() = assertFalse(decide(typed = "t"))

    @Test fun typedAllCaps() = assertFalse(decide(typed = "TEH", top = the.copy(word = "THE")))

    @Test fun typedIsADictionaryOrLearnedWord() =
        assertFalse(decide(typed = "then", top = Candidate("then", 0.95, isExactOrLearned = true, isTypedWord = true)))

    @Test fun topIsTheTypedWordIgnoringCase() = assertFalse(decide(typed = "Teh", top = the.copy(word = "teh")))

    @Test fun noCandidate() = assertFalse(decide(top = null))

    @Test fun topIsTypedWord() = assertFalse(decide(top = the.copy(isTypedWord = true)))

    @Test fun scoreBelowMinimum() = assertFalse(decide(top = the.copy(score = 0.69)))

    @Test fun capitalisedTypoStillCorrects() = assertTrue(decide(typed = "Teh", top = the.copy(word = "The")))
}
