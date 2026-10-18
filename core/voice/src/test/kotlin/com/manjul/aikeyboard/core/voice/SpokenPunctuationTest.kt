/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.voice

import kotlin.test.Test
import kotlin.test.assertEquals

class SpokenPunctuationTest {
    private fun sym(lang: String, spoken: String, symbol: String) {
        val out = SpokenPunctuation.apply("a $spoken b", lang)
        val expected = if (symbol == "\n") "a\nb" else "a$symbol b"
        assertEquals(expected, out, "$lang $spoken")
    }

    @Test fun english() {
        sym("en", "comma", ","); sym("en-US", "full stop", "."); sym("en", "period", ".")
        sym("en", "question mark", "?"); sym("en", "new line", "\n"); sym("en", "COMMA", ",")
    }

    @Test fun urdu() { sym("ur", "کاما", "،"); sym("ur", "سوالیہ نشان", "؟"); sym("ur", "نئی لائن", "\n") }
    @Test fun hindi() { sym("hi", "अल्पविराम", ","); sym("hi", "पूर्ण विराम", "।"); sym("hi", "नई लाइन", "\n") }
    @Test fun arabic() { sym("ar", "فاصلة", "،"); sym("ar", "علامة استفهام", "؟"); sym("ar", "سطر جديد", "\n") }
    @Test fun spanish() { sym("es", "coma", ","); sym("es", "punto", "."); sym("es", "nueva línea", "\n") }
    @Test fun portuguese() { sym("pt-BR", "vírgula", ","); sym("pt", "ponto final", "."); sym("pt", "nova linha", "\n") }
    @Test fun french() { sym("fr", "virgule", ","); sym("fr", "point", "."); sym("fr", "à la ligne", "\n") }
    @Test fun indonesian() { sym("id", "koma", ","); sym("id", "titik", "."); sym("id", "baris baru", "\n") }

    @Test fun wordsInsideOtherWordsAreUntouched() {
        assertEquals("commander", SpokenPunctuation.apply("commander", "en"))
        assertEquals("the periodic table", SpokenPunctuation.apply("the periodic table", "en"))
        assertEquals("comando", SpokenPunctuation.apply("comando", "es"))
        assertEquals("کاماتی", SpokenPunctuation.apply("کاماتی", "ur"))
    }

    @Test fun unknownLanguageIsUnchanged() = assertEquals("a comma b", SpokenPunctuation.apply("a comma b", "de"))
}
