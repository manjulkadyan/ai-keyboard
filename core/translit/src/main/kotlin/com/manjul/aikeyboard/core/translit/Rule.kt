/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translit

/** Where in the input a [Rule] may fire. */
enum class Position {
    /** Anywhere. */
    ANY,

    /** Only when the roman part starts at offset 0. */
    START,

    /** Only when the roman part neither starts at offset 0 nor ends at the input end. */
    MID,

    /** Only when the roman part ends exactly at the input end. */
    END,
}

/** One roman to native mapping. [native] may be empty (the sound is implied). Lower cost means more likely. */
class Rule(val roman: String, val native: String, val cost: Double, val position: Position)
