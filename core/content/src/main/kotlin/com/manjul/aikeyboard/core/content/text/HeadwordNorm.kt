/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.text

import java.text.Normalizer
import java.util.Locale

/**
 * The `headword_norm` key of a dictionary lookup: NFC, lower case with [Locale.ROOT], trimmed. A deliberate copy of
 * `:tools:packbuild` `HeadwordNorm` (plan F5); both tests use the same case list.
 */
object HeadwordNorm {
    fun of(word: String): String = Normalizer.normalize(word, Normalizer.Form.NFC).lowercase(Locale.ROOT).trim()
}
