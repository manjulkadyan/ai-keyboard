/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translit

/** A complete decoder output: the native spelling and its summed rule cost (lower is likelier). */
class Hypothesis(val native: String, val cost: Double) {
    override fun toString(): String = "Hypothesis(cost=$cost)"
}
