/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.suggest

/**
 * Per-locale learned-word state (the `user_word` table in `:core:database`). The engine passes words and previous
 * words lowercased with [String.lowercase]. Implementations must answer quickly from memory: the engine calls these
 * on the typing path. Values are use counts; 0 means unknown.
 */
interface LearnedWords {
    fun boost(locale: String, word: String): Int

    fun bigram(locale: String, prev: String, word: String): Int

    /** A word the user removed or reverted; it is never suggested. */
    fun isBlocked(locale: String, word: String): Boolean

    companion object {
        val NONE: LearnedWords = object : LearnedWords {
            override fun boost(locale: String, word: String): Int = 0
            override fun bigram(locale: String, prev: String, word: String): Int = 0
            override fun isBlocked(locale: String, word: String): Boolean = false
        }
    }
}
