/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate.bar

/**
 * How long input must be still before a translate request starts (TRD §4.1.3, plan decision 5). Low-tier
 * devices wait longer so ML Kit runs less often (R-03). Timing only, no text.
 */
data class DebouncePolicy(val debounceMs: Long) {
    init {
        require(debounceMs >= 0) { "debounceMs must not be negative" }
    }

    companion object {
        val Default = DebouncePolicy(450)
        val LowTier = DebouncePolicy(600)

        /** The Translator screen (PRD FR-12): auto-translate after 600 ms. */
        val Translator = DebouncePolicy(600)

        /** Below this much RAM a device is low tier. */
        const val LOW_TIER_RAM_BYTES = 4L * 1024 * 1024 * 1024

        /**
         * [LowTier] for `ActivityManager.isLowRamDevice` or less than 4 GiB of total RAM, else [Default].
         * An unknown RAM size (`<= 0`) is not treated as low.
         */
        fun forDevice(lowRam: Boolean, totalRamBytes: Long): DebouncePolicy =
            if (lowRam || totalRamBytes in 1 until LOW_TIER_RAM_BYTES) LowTier else Default
    }
}
