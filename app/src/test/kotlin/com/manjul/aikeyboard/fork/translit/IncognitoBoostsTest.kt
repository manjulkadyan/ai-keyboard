/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translit

import com.manjul.aikeyboard.core.translit.TranslitLang
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import kotlin.test.assertEquals

class IncognitoBoostsTest {
    private val graph = TranslitGraph({ error("no assets") }, Dispatchers.Unconfined)

    private fun change(was: Boolean, now: Boolean) = IncognitoBoosts.onChange(was, now) { graph.clearBoosts() }

    @Test fun `boosts are gone right after incognito turns on, without a suggest call`() {
        graph.boost(TranslitLang.UR, "kya", "کیا")
        change(was = false, now = true)
        assertEquals(0, graph.boostCount())
    }

    @Test fun `turning incognito off, or a no-change update, keeps the boosts`() {
        graph.boost(TranslitLang.UR, "kya", "کیا")
        change(was = true, now = false)
        change(was = false, now = false)
        change(was = true, now = true)
        assertEquals(1, graph.boostCount())
    }
}
