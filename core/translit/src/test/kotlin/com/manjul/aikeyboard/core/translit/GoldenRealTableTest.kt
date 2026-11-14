/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translit

import kotlin.test.Test
import kotlin.test.assertEquals

/** Top-1 goldens against the shipped rule tables and the shipped dictionaries, so a table edit cannot break them. */
class GoldenRealTableTest {
    private fun top(lang: TranslitLang, roman: String): String =
        TranslitEngine(lang, RuleTable.load(lang), TestLexicons.shipped(lang), CustomMappings())
            .suggest(roman, 1).single().native

    @Test fun urduGoldens() {
        assertEquals("پیار", top(TranslitLang.UR, "pyar"))
        assertEquals("کیا", top(TranslitLang.UR, "kya"))
        assertEquals("ہے", top(TranslitLang.UR, "hai"))
    }

    @Test fun hindiGoldens() {
        assertEquals("करना", top(TranslitLang.HI, "karna"))
        assertEquals("क्या", top(TranslitLang.HI, "kya"))
        assertEquals("भाग्य", top(TranslitLang.HI, "bhagya"))
    }
}
