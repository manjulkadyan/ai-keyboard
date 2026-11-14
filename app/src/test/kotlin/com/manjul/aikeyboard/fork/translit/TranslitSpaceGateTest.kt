/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translit

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.manjul.aikeyboard.core.translit.Source
import com.manjul.aikeyboard.core.translit.TranslitLang
import com.manjul.aikeyboard.fork.suggest.AutocorrectGate
import com.manjul.aikeyboard.fork.suggest.SuggestContext
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertNull
import kotlin.test.assertSame

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TranslitSpaceGateTest {
    private val context = SuggestContext().apply {
        update(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }, PolicyDecision.Unrestricted)
    }
    private val never = EnglishCheck({ null })

    private fun candidate(
        source: Source = Source.LEXICON,
        typed: String = "kya",
        latin: Boolean = false,
        eligible: Boolean = source != Source.RULES && !latin,
    ) = TranslitCandidate("x", 1.0, eligible, null, typed, TranslitLang.UR, source, latin)

    @Test fun `a lexicon candidate in a plain field is committed`() {
        val c = candidate()
        assertSame(c, TranslitSpaceGate.onSpace(c, "kya", context, never))
    }

    @Test fun `a mapping candidate is committed`() {
        val c = candidate(Source.MAPPING)
        assertSame(c, TranslitSpaceGate.onSpace(c, "kya", context, never))
    }

    @Test fun `a rules candidate is not committed`() =
        assertNull(TranslitSpaceGate.onSpace(candidate(Source.RULES), "kya", context, never))

    @Test fun `the Latin chip is not committed`() =
        assertNull(TranslitSpaceGate.onSpace(candidate(latin = true, eligible = true), "kya", context, never))

    @Test fun `a stale candidate is not committed`() =
        assertNull(TranslitSpaceGate.onSpace(candidate(typed = "ky"), "kya", context, never))

    @Test fun `a blocked field commits nothing`() {
        context.fieldAllowsTranslitCommit = false
        assertNull(TranslitSpaceGate.onSpace(candidate(), "kya", context, never))
    }

    @Test fun `AutocorrectGate sends a translit candidate to the gate and never commits it on other keys`() {
        val c = candidate()
        // No translit provider behind it: the English check is missing, so nothing is committed.
        assertNull(AutocorrectGate.onSpace(c, "kya", context))
        assertNull(AutocorrectGate.onOtherKey(c))
    }
}
