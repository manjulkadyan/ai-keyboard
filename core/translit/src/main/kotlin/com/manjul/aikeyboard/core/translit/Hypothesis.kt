/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translit

/**
 * A complete decoder output: the native spelling, its summed rule cost (lower is likelier) and the number of rule
 * matches it used. At equal cost, fewer [segments] wins, so whole-word rows such as "nahi" beat letter-by-letter.
 */
class Hypothesis(val native: String, val cost: Double, val segments: Int = 0) {
    override fun toString(): String = "Hypothesis(cost=$cost, segments=$segments)"

    companion object {
        /** Cost, then fewer segments, then the native string, so ties never depend on hash order. */
        val ORDER: Comparator<Hypothesis> =
            compareBy<Hypothesis> { it.cost }.thenBy { it.segments }.thenBy { it.native }
    }
}
