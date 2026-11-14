/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translit

/** Where a [Candidate] came from, best first: a user mapping, a lexicon word, or the rules alone. */
enum class Source { MAPPING, LEXICON, RULES }

/** One ranked native suggestion. Higher [score] is better. [toString] holds no text. */
class Candidate(val native: String, val score: Double, val source: Source) {
    override fun toString(): String = "Candidate(source=$source)"
}
