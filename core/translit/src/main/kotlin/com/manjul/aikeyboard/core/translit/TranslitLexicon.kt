/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translit

/**
 * The native word lists the decoder and ranker consult. Native words are user-facing text: implementations never log
 * them or put them into an exception message.
 */
interface TranslitLexicon {
    /** 1..255 for a known word, null for an unknown one. */
    fun frequency(nativeWord: String): Int?

    /** True when some known word starts with [nativePrefix] (a whole word counts). */
    fun prefixExists(nativePrefix: String): Boolean
}
