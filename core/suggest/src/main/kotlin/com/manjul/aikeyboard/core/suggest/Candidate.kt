/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.suggest

/**
 * One suggestion. [score] is in [0, 1]. [isTypedWord] marks the typed text itself (offered first when it is a
 * dictionary or learned word); [isExactOrLearned] is true for that candidate and for any word the user has learned.
 * Holds user content: never log it.
 */
data class Candidate(
    val word: String,
    val score: Double,
    val isExactOrLearned: Boolean,
    val isTypedWord: Boolean,
)
