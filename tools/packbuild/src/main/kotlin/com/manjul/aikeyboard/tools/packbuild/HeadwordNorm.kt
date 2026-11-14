/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.tools.packbuild

import java.text.Normalizer
import java.util.Locale

/**
 * The `entry.headword_norm` key: NFC, lower case with [Locale.ROOT], trimmed. `:core:content` `HeadwordNorm` is a
 * deliberate copy for the device lookup (plan F5); both tests use the same case list (`HeadwordNormTest.CASES`).
 */
object HeadwordNorm {
    fun of(word: String): String = Normalizer.normalize(word, Normalizer.Form.NFC).lowercase(Locale.ROOT).trim()
}
