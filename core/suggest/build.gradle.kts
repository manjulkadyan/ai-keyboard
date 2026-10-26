/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

// :core:suggest (PRD FR-05, TRD §4.1.2): on-device suggestion engine. Compact binary dictionary (AKSD),
// Damerau-Levenshtein (OSA) matching with keyboard-proximity costs, unigram + bigram + learned ranking and the
// autocorrect decision. Pure Kotlin, no Android. Typed words are user content and are never logged.

plugins {
    alias(libs.plugins.aikeyboard.jvm.library)
    `java-test-fixtures`
}
