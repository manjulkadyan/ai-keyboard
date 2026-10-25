/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate

import kotlin.test.Test
import kotlin.test.assertEquals

class ModelSizesTest {
    private val en = Lang("en")
    private val ur = Lang("ur")
    private val hi = Lang("hi")
    private val es = Lang("es")

    @Test fun nonEnglishPairNeedsTheEnglishPivot() {
        val missing = ModelSizes.missingFor(LangPair(ur, hi), emptySet())
        assertEquals(setOf(ur, hi, en), missing)
        assertEquals(90L * 1024 * 1024, ModelSizes.bytes(missing))
    }

    @Test fun downloadedSideIsNotMissing() {
        assertEquals(emptySet(), ModelSizes.missingFor(LangPair(en, ur), setOf(ur)))
        assertEquals(setOf(es), ModelSizes.missingFor(LangPair(es, en), setOf(en)))
        assertEquals(0L, ModelSizes.bytes(emptySet()))
    }
}
