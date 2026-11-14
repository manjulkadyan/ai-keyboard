/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translit

import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.analytics.RateSampler
import com.manjul.aikeyboard.core.analytics.TranslitCommitSource
import com.manjul.aikeyboard.core.translit.TranslitLang

/**
 * `translit_commit{lang, source}` for one Roman typing commit, in the host field or in the Translate bar (PRD §12),
 * sampled at [SAMPLE_RATE]. [sink] is the consent-aware relay. Only the language and the chip kind are sent: never
 * the Roman word, the committed text or a length.
 */
class TranslitAnalytics(
    private val sink: (AnalyticsEvent) -> Unit,
    private val sampler: RateSampler = RateSampler(SAMPLE_RATE),
) {
    /** A native candidate ([latin] false) or the Latin chip ([latin] true) was committed. */
    fun onCommit(lang: TranslitLang, latin: Boolean) {
        if (!sampler.sample()) return
        val source = if (latin) TranslitCommitSource.LATIN else TranslitCommitSource.CANDIDATE
        sink(EventCatalog.translitCommit(lang.tag, source))
    }

    companion object {
        /** PRD §12: `translit_commit` is sampled at 1 %. */
        const val SAMPLE_RATE = 0.01
    }
}
