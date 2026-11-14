/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translit

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.manjul.aikeyboard.core.translit.TranslitLang
import com.manjul.aikeyboard.fork.suggest.AutocorrectGate
import com.manjul.aikeyboard.fork.suggest.SuggestContext
import com.manjul.aikeyboard.ime.core.Subtype
import com.manjul.aikeyboard.ime.editor.EditorContent
import com.manjul.aikeyboard.ime.editor.EditorRange
import com.manjul.aikeyboard.ime.nlp.SuggestionCandidate
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import com.manjul.aikeyboard.lib.FlorisLocale
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** The host strip and the Translate bar strip give the same candidates and the same space decision for a word. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TranslitParityTest {
    private val context = SuggestContext().apply {
        update(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }, PolicyDecision.Unrestricted)
    }

    private fun subtype(lang: TranslitLang) = Subtype.DEFAULT.copy(
        primaryLocale = FlorisLocale.fromTag("${lang.tag}-${if (lang == TranslitLang.UR) "PK" else "IN"}-roman"),
    )

    private fun composing(word: String): EditorContent {
        val range = EditorRange(0, word.length)
        return EditorContent(word, 0, EditorRange.cursor(word.length), range, range)
    }

    private fun shape(c: SuggestionCandidate): List<Any> {
        val t = c as TranslitCandidate
        return listOf(t.text.toString(), t.confidence, t.isEligibleForAutoCommit, t.typed, t.lang, t.source, t.latin)
    }

    private fun graph() = BarTranslitControllerTest.FakeGraph(setOf("کیا", "क्या")).apply {
        mappings.getValue(TranslitLang.UR).put("yaar", "یار")
    }

    private fun check(lang: TranslitLang, word: String) = runTest(StandardTestDispatcher()) {
        val g = graph()
        val provider = TranslitSuggestionProvider({ g }, context, { false })
        val host = runBlocking { provider.suggest(subtype(lang), composing(word), 3, false, false) }
        val hostSpace = host.firstOrNull()?.let { AutocorrectGate.onSpace(it, word, context) }

        val bar = BarTranslitControllerTest.Rig(this, lang, g)
        bar.type(word, this)
        assertEquals(host.map(::shape), bar.controller.candidates.value.map(::shape), "candidates for $lang")
        bar.controller.onSpace()
        runCurrent()
        val barCommitted = bar.buffer.text.value != "$word "
        assertEquals(hostSpace != null, barCommitted, "space decision for $lang")
        if (hostSpace != null) assertEquals(hostSpace.text.toString() + " ", bar.buffer.text.value)
    }

    @Test fun aLexiconWordMatches() {
        check(TranslitLang.UR, "kya")
        check(TranslitLang.HI, "kya")
    }

    @Test fun aMappingMatches() = check(TranslitLang.UR, "yaar")

    @Test fun aRuleOnlyWordMatches() = check(TranslitLang.UR, "pq")
}
