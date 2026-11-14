/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translit

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.manjul.aikeyboard.core.translit.RuleTable
import com.manjul.aikeyboard.core.translit.Source
import com.manjul.aikeyboard.core.translit.TranslitEngine
import com.manjul.aikeyboard.core.translit.TranslitLang
import com.manjul.aikeyboard.core.translit.TranslitLexicon
import com.manjul.aikeyboard.fork.suggest.SuggestContext
import com.manjul.aikeyboard.ime.core.Subtype
import com.manjul.aikeyboard.ime.editor.EditorContent
import com.manjul.aikeyboard.ime.editor.EditorRange
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import com.manjul.aikeyboard.lib.FlorisLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.ByteArrayInputStream
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TranslitSuggestionProviderTest {
    private class WordLexicon(private val words: Set<String>) : TranslitLexicon {
        private val prefixes = words.flatMapTo(HashSet()) { w -> (0..w.length).map { w.substring(0, it) } }
        override fun frequency(nativeWord: String): Int? = if (nativeWord in words) 100 else null
        override fun prefixExists(nativePrefix: String) = nativePrefix in prefixes
    }

    /** A graph over a tiny lexicon (or none), with no assets. */
    private class FakeGraph(private val words: Set<String>) :
        TranslitGraph({ error("no assets") }, Dispatchers.Unconfined) {
        override suspend fun engine(lang: TranslitLang) =
            TranslitEngine(lang, RuleTable.load(lang), WordLexicon(words), mappings.getValue(lang))
    }

    private val graph = FakeGraph(setOf("کیا", "کیوں", "کی"))
    private val context = SuggestContext()
    private var incognito = false
    private val provider = TranslitSuggestionProvider({ graph }, context, { incognito })

    private fun allow(decision: PolicyDecision = PolicyDecision.Unrestricted) =
        context.update(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }, decision)

    private val urRoman = Subtype.DEFAULT.copy(primaryLocale = FlorisLocale.fromTag("ur-PK-roman"))

    private fun composing(word: String): EditorContent {
        val range = EditorRange(0, word.length)
        return EditorContent(word, 0, EditorRange.cursor(word.length), range, range)
    }

    private fun suggest(word: String, max: Int = 3) =
        runBlocking { provider.suggest(urRoman, composing(word), max, false, incognito) }

    @Test fun `kya gives native candidates then the Latin chip, at most 3`() {
        allow()
        val out = suggest("kya")
        assertEquals(2, out.size) // one lexicon word plus the Latin chip
        val last = out.last() as TranslitCandidate
        assertTrue(last.latin)
        assertEquals("kya", last.text.toString())
        assertTrue(out.dropLast(1).none { (it as TranslitCandidate).latin })
        assertEquals("کیا", out.first().text.toString())
    }

    @Test fun `the Latin chip takes the third slot`() {
        allow()
        // An empty lexicon leaves the decoder's rule variants, so there are at least two native candidates.
        val open = TranslitSuggestionProvider({ FakeGraph(emptySet()) }, context, { incognito })
        val out = runBlocking { open.suggest(urRoman, composing("kya"), 3, false, false) }
        assertEquals(3, out.size)
        assertTrue((out[2] as TranslitCandidate).latin)
        assertEquals(2, out.count { !(it as TranslitCandidate).latin })
    }

    @Test fun `sensitive decision and suggestions off give nothing`() {
        allow(PolicyDecision.Locked)
        assertTrue(suggest("kya").isEmpty())
        allow(PolicyDecision.Unrestricted.copy(allowSuggestionStrip = false))
        assertTrue(suggest("kya").isEmpty())
        context.clear()
        assertTrue(suggest("kya").isEmpty())
    }

    @Test fun `a word without a Latin letter gives nothing`() {
        allow()
        assertTrue(suggest("123").isEmpty())
        assertTrue(suggest("").isEmpty())
    }

    @Test fun `rule-only candidates never auto-commit, lexicon ones can`() {
        allow()
        val lexicon = suggest("kya").map { it as TranslitCandidate }
        val top = lexicon.first()
        assertEquals(Source.LEXICON, top.source)
        assertTrue(top.isEligibleForAutoCommit)
        assertFalse(lexicon.last().isEligibleForAutoCommit)

        val rulesOnly = TranslitSuggestionProvider({ FakeGraph(emptySet()) }, context, { incognito })
        val out = runBlocking { rulesOnly.suggest(urRoman, composing("kya"), 3, false, false) }
            .map { it as TranslitCandidate }
        assertTrue(out.filter { !it.latin }.all { it.source == Source.RULES && !it.isEligibleForAutoCommit })
    }

    @Test fun `a mapping candidate can auto-commit`() {
        allow()
        graph.mappings.getValue(TranslitLang.UR).put("kya", "کیا")
        val top = suggest("kya").first() as TranslitCandidate
        assertEquals(Source.MAPPING, top.source)
        assertTrue(top.isEligibleForAutoCommit)
    }

    @Test fun `accepting in incognito leaves the boosts empty`() {
        allow()
        val c = suggest("kya").first()
        incognito = true
        runBlocking { provider.notifySuggestionAccepted(urRoman, c) }
        assertEquals(0, graph.boostCount())
        incognito = false
        runBlocking { provider.notifySuggestionAccepted(urRoman, c) }
        assertEquals(1, graph.boostCount())
        runBlocking { provider.notifySuggestionReverted(urRoman, c) }
        assertEquals(0, graph.boostCount())
    }

    @Test fun `accepting where learning is off, or the Latin chip, boosts nothing`() {
        allow(PolicyDecision.Unrestricted.copy(allowLearning = false))
        val out = suggest("kya")
        runBlocking { provider.notifySuggestionAccepted(urRoman, out.first()) }
        allow()
        runBlocking { provider.notifySuggestionAccepted(urRoman, suggest("kya").last()) }
        assertEquals(0, graph.boostCount())
    }

    @Test fun `flags and toString`() {
        allow()
        assertTrue(provider.forcesSuggestionOn)
        assertFalse(runBlocking { provider.removeSuggestion(urRoman, suggest("kya").first()) })
        assertTrue(runBlocking { provider.getListOfWords(urRoman) }.isEmpty())
        assertEquals(0.0, runBlocking { provider.getFrequencyForWord(urRoman, "kya") })
        for (c in suggest("kya")) {
            val s = c.toString()
            assertFalse(s.contains("kya") || s.contains("کیا"), s)
            assertTrue(s.startsWith("TranslitCandidate(lang=ur, source="))
        }
    }

    @Test fun `a corrupt dictionary degrades to no candidates`() {
        allow()
        val corrupt = TranslitGraph({ ByteArrayInputStream(ByteArray(64) { 7 }) }, Dispatchers.Unconfined)
        val p = TranslitSuggestionProvider({ corrupt }, context, { incognito })
        runBlocking { p.preload(urRoman) }
        assertTrue(runBlocking { p.suggest(urRoman, composing("kya"), 3, false, false) }.isEmpty())
        val missing = TranslitGraph({ throw java.io.FileNotFoundException() }, Dispatchers.Unconfined)
        val q = TranslitSuggestionProvider({ missing }, context, { incognito })
        runBlocking { q.preload(urRoman) }
        assertTrue(runBlocking { q.suggest(urRoman, composing("kya"), 3, false, false) }.isEmpty())
    }

    @Test fun `turning incognito on drops the boosts made before it`() {
        allow()
        graph.boost(TranslitLang.UR, "kya", "کیا")
        assertEquals(1, graph.boostCount())
        runBlocking { provider.suggest(urRoman, composing("kya"), 3, false, false) }
        assertEquals(1, graph.boostCount())
        runBlocking { provider.suggest(urRoman, composing("kya"), 3, false, true) }
        assertEquals(0, graph.boostCount())
    }

    @Test fun `preload fills the user mappings and a failing load does not crash`() {
        val loaded = mutableListOf<TranslitLang>()
        val ok = TranslitSuggestionProvider({ graph }, context, { incognito }, { loaded += it })
        runBlocking { ok.preload(urRoman) }
        assertEquals(listOf(TranslitLang.UR), loaded)
        val bad = TranslitSuggestionProvider({ graph }, context, { incognito }, { error("db") })
        runBlocking { bad.preload(urRoman) }
    }
}
