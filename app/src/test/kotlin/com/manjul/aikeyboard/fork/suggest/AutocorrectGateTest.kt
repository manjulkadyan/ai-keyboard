/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.suggest

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.manjul.aikeyboard.core.translit.Source
import com.manjul.aikeyboard.core.translit.TranslitLang
import com.manjul.aikeyboard.fork.translit.TranslitCandidate
import com.manjul.aikeyboard.ime.nlp.WordSuggestionCandidate
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertNull
import kotlin.test.assertSame

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AutocorrectGateTest {
    private val context = SuggestContext().apply {
        update(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }, PolicyDecision.Unrestricted)
    }

    private val translit =
        TranslitCandidate("x", 1.0, true, null, "kya", TranslitLang.UR, Source.LEXICON, false)

    @Test fun `onOtherKey returns null for a translit candidate`() = assertNull(AutocorrectGate.onOtherKey(translit))

    @Test fun `onSpace returns null for a translit candidate without its provider`() =
        assertNull(AutocorrectGate.onSpace(translit, "kya", context))

    @Test fun `onOtherKey keeps a null and a foreign candidate unchanged`() {
        assertNull(AutocorrectGate.onOtherKey(null))
        val other = WordSuggestionCandidate("hello", confidence = 1.0, isEligibleForAutoCommit = true)
        assertSame(other, AutocorrectGate.onOtherKey(other))
        assertSame(other, AutocorrectGate.onSpace(other, "hello", context))
    }

    @Test fun `a Latin word keeps the S3 behavior`() {
        val latin = LatinWordCandidate("hello", 1.0, true, null, "helo", null, "en")
        assertNull(AutocorrectGate.onOtherKey(latin))
        context.fieldAllowsAutocorrect = true
        assertSame(latin, AutocorrectGate.onSpace(latin, "helo", context))
        assertNull(AutocorrectGate.onSpace(latin, "hel", context))
    }
}
