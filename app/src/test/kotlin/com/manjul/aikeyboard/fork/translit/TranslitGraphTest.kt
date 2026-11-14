/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translit

import com.manjul.aikeyboard.core.translit.TranslitLang
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class TranslitGraphTest {
    @Test fun `a missing dictionary is read once per language`() {
        val opens = mutableListOf<String>()
        val g = TranslitGraph({ name -> opens += name; throw FileNotFoundException() }, Dispatchers.Unconfined)
        repeat(3) { assertFailsWith<IllegalStateException> { runBlocking { g.engine(TranslitLang.UR) } } }
        assertEquals(listOf("dict/ur.sdict"), opens)
        assertFailsWith<IllegalStateException> { runBlocking { g.engine(TranslitLang.HI) } }
        assertEquals(listOf("dict/ur.sdict", "dict/hi.sdict"), opens) // Each language keeps its own record.
    }

    @Test fun `an unreadable dictionary is read once`() {
        var opens = 0
        val g = TranslitGraph({ opens++; ByteArrayInputStream(ByteArray(64) { 7 }) }, Dispatchers.Unconfined)
        repeat(3) { assertFailsWith<IllegalStateException> { runBlocking { g.engine(TranslitLang.UR) } } }
        assertEquals(1, opens)
    }

    @Test fun `toString holds no text`() {
        val g = TranslitGraph({ throw FileNotFoundException() }, Dispatchers.Unconfined)
        g.boost(TranslitLang.UR, "kya", "کیا")
        val s = g.toString()
        assertFalse("kya" in s)
        assertFalse("کیا" in s)
    }
}
