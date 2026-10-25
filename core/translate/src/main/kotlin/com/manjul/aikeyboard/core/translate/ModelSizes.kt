/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate

/**
 * Download size estimate for a pair (TRD §11.5 #5.1). ML Kit gives no per-model size, so every model counts
 * as [APPROX_MODEL_BYTES]. ML Kit translates between two non-English languages through English, so the
 * English model is needed as a pivot when neither side is English. Language tags only, no text.
 */
object ModelSizes {
    const val APPROX_MODEL_BYTES = 30L * 1024 * 1024

    /**
     * Models still to download for [pair], given the languages already on the device. A pair with English on
     * one side counts only the other model (`en -> ur` with `ur` present needs nothing); a pair without English
     * also counts the English pivot.
     */
    fun missingFor(pair: LangPair, downloaded: Set<Lang>): Set<Lang> {
        val sides = setOf(pair.source, pair.target)
        val needed = if (Lang.English in sides) sides - Lang.English else sides + Lang.English
        return needed - downloaded
    }

    fun bytes(missing: Set<Lang>): Long = missing.size * APPROX_MODEL_BYTES
}
