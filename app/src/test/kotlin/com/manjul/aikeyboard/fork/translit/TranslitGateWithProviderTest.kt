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
import com.manjul.aikeyboard.fork.suggest.AutocorrectGate
import com.manjul.aikeyboard.fork.suggest.SuggestContext
import com.manjul.aikeyboard.ime.core.Subtype
import com.manjul.aikeyboard.ime.editor.EditorContent
import com.manjul.aikeyboard.ime.editor.EditorRange
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import com.manjul.aikeyboard.lib.FlorisLocale
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TranslitGateWithProviderTest {
    private class Words(private val words: Set<String>) : TranslitLexicon {
        private val prefixes = words.flatMapTo(HashSet()) { w -> (0..w.length).map { w.substring(0, it) } }
        override fun frequency(nativeWord: String): Int? = if (nativeWord in words) 100 else null
        override fun prefixExists(nativePrefix: String) = nativePrefix in prefixes
    }

    private class CountingGraph :
        TranslitGraph({ name -> error("unexpected asset $name") }, Dispatchers.Unconfined) {
        override suspend fun engine(lang: TranslitLang) =
            TranslitEngine(lang, RuleTable.load(lang), Words(setOf("کیا", "فون")), mappings.getValue(lang))
    }

    private val context = SuggestContext().apply {
        update(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }, PolicyDecision.Unrestricted)
    }
    private val urRoman = Subtype.DEFAULT.copy(primaryLocale = FlorisLocale.fromTag("ur-PK-roman"))

    private fun composing(word: String): EditorContent {
        val range = EditorRange(0, word.length)
        return EditorContent(word, 0, EditorRange.cursor(word.length), range, range)
    }

    private fun provider(graph: TranslitGraph) = TranslitSuggestionProvider({ graph }, context, { false })

    private fun top(p: TranslitSuggestionProvider, word: String) =
        runBlocking { p.suggest(urRoman, composing(word), 3, false, false) }.first()

    private fun enGraph(onOpen: () -> Unit = {}): TranslitGraph = object : TranslitGraph(
        { name ->
            onOpen()
            assertEquals("dict/en.sdict", name)
            File("src/main/assets/dict/en.sdict").inputStream()
        },
        Dispatchers.Unconfined,
    ) {
        override suspend fun engine(lang: TranslitLang) =
            TranslitEngine(lang, RuleTable.load(lang), Words(setOf("کیا", "فون")), mappings.getValue(lang))
    }

    @Test fun `a lexicon candidate from the provider is committed on space in a plain field`() {
        val p = provider(CountingGraph())
        val c = top(p, "kya")
        assertSame(c, AutocorrectGate.onSpace(c, "kya", context))
    }

    @Test fun `phone is not committed once the English dictionary is loaded`() {
        val g = enGraph()
        val p = provider(g)
        val c = top(p, "phone") // suggest warms en.sdict
        assertNotNull(g.englishIfLoaded())
        assertNull(AutocorrectGate.onSpace(c, "phone", context))
    }

    @Test fun `the space path never loads the English dictionary`() {
        var opens = 0
        val g = enGraph { opens++ }
        val p = provider(g)
        val c = TranslitCandidate("فون", 1.0, true, p, "phone", TranslitLang.UR, Source.LEXICON, false)
        assertSame(c, AutocorrectGate.onSpace(c, "phone", context)) // not loaded: commit rules apply as normal
        assertEquals(0, opens)
    }

    @Test fun `a short allow-list word also warms the dictionary`() {
        var opens = 0
        val g = enGraph { opens++ }
        top(provider(g), "ok")
        assertEquals(1, opens)
    }
}
