/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translit

import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.RateSampler
import com.manjul.aikeyboard.core.translit.RuleTable
import com.manjul.aikeyboard.core.translit.Source
import com.manjul.aikeyboard.core.translit.TranslitEngine
import com.manjul.aikeyboard.core.translit.TranslitLang
import com.manjul.aikeyboard.core.translit.TranslitLexicon
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import com.manjul.aikeyboard.ime.translateui.BarBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BarTranslitControllerTest {
    /** A lexicon of a few native words, frequency 100. */
    class WordLexicon(private val words: Set<String>) : TranslitLexicon {
        private val prefixes = words.flatMapTo(HashSet()) { w -> (0..w.length).map { w.substring(0, it) } }
        override fun frequency(nativeWord: String): Int? = if (nativeWord in words) 100 else null
        override fun prefixExists(nativePrefix: String) = nativePrefix in prefixes
    }

    /** A graph over the shipped rule tables and [words], with no assets and no English dictionary. */
    class FakeGraph(private val words: Set<String>) : TranslitGraph({ error("no assets") }, Dispatchers.Unconfined) {
        override suspend fun engine(lang: TranslitLang) =
            TranslitEngine(lang, RuleTable.load(lang), WordLexicon(words), mappings.getValue(lang))

        override fun englishDictionary() = null
    }

    private class Rig(scope: TestScope, lang: TranslitLang?) {
        val buffer = BarBuffer()
        val events = mutableListOf<AnalyticsEvent>()
        var now = 0L
        var lang: TranslitLang? = lang
        val graph = FakeGraph(setOf("کیا", "क्या"))
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val controller = BarTranslitController(
            buffer = buffer,
            graph = { graph },
            lang = { this.lang },
            decision = { PolicyDecision.Unrestricted },
            analytics = { events += it },
            sampler = RateSampler(1.0) { 0.0 },
            scope = scope.backgroundScope,
            clock = { now },
            compute = dispatcher,
        )

        fun type(s: String, scope: TestScope) {
            for (ch in s) assertTrue(controller.onLetter(ch.toString()))
            scope.runCurrent()
        }
    }

    private fun run(lang: TranslitLang?, block: suspend TestScope.(Rig) -> Unit) = runTest(StandardTestDispatcher()) {
        block(Rig(this, lang))
    }

    @Test fun kyaPlusSpaceCommitsTheNativeWord() = run(TranslitLang.UR) { r ->
        r.type("kya", this)
        assertEquals("کیا", r.controller.candidates.value.first().text)
        assertEquals(Source.LEXICON, r.controller.candidates.value.first().source)
        assertTrue(r.controller.candidates.value.last().latin)
        assertTrue(r.controller.onSpace())
        runCurrent()
        assertEquals("کیا ", r.buffer.text.value)
        assertTrue(r.controller.candidates.value.isEmpty())
    }

    @Test fun onlyTheWordAfterTheLastSpaceIsReplaced() = run(TranslitLang.UR) { r ->
        r.buffer.type("hello ")
        r.type("kya", this)
        r.controller.onSpace()
        assertEquals("hello کیا ", r.buffer.text.value)
    }

    @Test fun ruleOnlyWordPlusSpaceKeepsTheRomanWord() = run(TranslitLang.UR) { r ->
        r.type("pq", this)
        assertTrue(r.controller.candidates.value.none { it.source == Source.LEXICON })
        assertTrue(r.controller.onSpace())
        assertEquals("pq ", r.buffer.text.value)
        assertTrue(r.events.isEmpty())
    }

    @Test fun backspaceAfterACommitRestoresTheRomanWordOnce() = run(TranslitLang.UR) { r ->
        r.type("kya", this)
        r.controller.onSpace()
        assertTrue(r.controller.onBackspace())
        assertEquals("kya", r.buffer.text.value)
        assertTrue(r.controller.onBackspace())
        assertEquals("ky", r.buffer.text.value)
    }

    @Test fun undoIsGoneAfterItsWindow() = run(TranslitLang.UR) { r ->
        r.type("kya", this)
        r.controller.onSpace()
        r.now += 6_000
        r.controller.onBackspace()
        assertEquals("کیا", r.buffer.text.value)
    }

    @Test fun pickReplacesTheWordAndTheLatinChipKeepsIt() = run(TranslitLang.HI) { r ->
        r.type("kya", this)
        val native = r.controller.candidates.value.first()
        assertEquals("क्या", native.text)
        r.controller.onPick(native)
        assertEquals("क्या ", r.buffer.text.value)
        r.type("kya", this)
        r.controller.onPick(r.controller.candidates.value.last())
        assertEquals("क्या kya ", r.buffer.text.value)
        assertEquals(listOf("candidate", "latin"), r.events.map { it.params["source"] })
        assertTrue(r.events.all { it.params["lang"] == "hi" })
    }

    @Test fun aStalePickDoesNothing() = run(TranslitLang.UR) { r ->
        r.type("kya", this)
        val stale = r.controller.candidates.value.first()
        r.buffer.type("x")
        r.controller.onPick(stale)
        assertEquals("kyax", r.buffer.text.value)
    }

    @Test fun aNonRomanSubtypeLeavesTheKeysToTheS2Path() = run(null) { r ->
        assertFalse(r.controller.onLetter("k"))
        assertFalse(r.controller.onSpace())
        assertFalse(r.controller.onBackspace())
        assertEquals("", r.buffer.text.value)
        assertTrue(r.controller.candidates.value.isEmpty())
    }

    @Test fun resetDropsCandidatesAndTheUndo() = run(TranslitLang.UR) { r ->
        r.type("kya", this)
        r.controller.onSpace()
        r.controller.reset()
        r.controller.onBackspace()
        assertEquals("کیا", r.buffer.text.value)
        assertTrue(r.controller.candidates.value.isEmpty())
    }

    @Test fun toStringHoldsNoText() = run(TranslitLang.UR) { r ->
        r.type("kya", this)
        val s = r.controller.toString()
        assertFalse("kya" in s)
        assertFalse("کیا" in s)
    }
}
