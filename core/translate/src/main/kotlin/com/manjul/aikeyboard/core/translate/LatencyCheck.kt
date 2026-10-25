/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate

/**
 * Shared rules of the day-1 translate latency check (TRD §8, §9 R-03): the instrumented `TranslateLatencyTest`
 * and the debug devtools probe measure the same pairs over the same corpus and report the same plain-number
 * lines. See `docs/translation/latency.md`. Output carries pair tags and milliseconds only, never text.
 */
object LatencyCheck {
    /** The pairs TRD §8 asks for: English to each MVP script plus Spanish, and the two busiest reverse pairs. */
    val Pairs: List<LangPair> = listOf(
        LangPair(Lang("en"), Lang("ur")),
        LangPair(Lang("en"), Lang("hi")),
        LangPair(Lang("en"), Lang("ar")),
        LangPair(Lang("en"), Lang("bn")),
        LangPair(Lang("en"), Lang("es")),
        LangPair(Lang("ur"), Lang("en")),
        LangPair(Lang("hi"), Lang("en")),
    )

    /** Untimed calls per pair before measuring, so model load and JIT are not in the numbers. */
    const val WARM_UP_CALLS = 5

    /** Timed calls per pair: one per corpus sentence. */
    const val TIMED_CALLS = 50

    /** Mid-tier p90 budget (TRD §6.4). Above it, R-03 applies to every device. */
    const val MID_P90_BUDGET_MS = 150L

    /** Low-tier p90 budget (3 GB, Android 10 class). */
    const val LOW_P90_BUDGET_MS = 400L

    /** Corpus asset path of [lang] (`latency/ur.txt`). */
    fun corpusAsset(lang: Lang): String = "latency/${lang.tag}.txt"

    /** One sentence per line; blank lines and lines starting with `#` are skipped. */
    fun parseCorpus(text: String): List<String> =
        text.lineSequence().map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") }.toList()

    /** The p90 budget for the `tier` instrumentation argument, or `null` (measure only) when it is not set. */
    fun p90BudgetMs(tier: String?): Long? = when (tier) {
        null, "" -> null
        "mid" -> MID_P90_BUDGET_MS
        "low" -> LOW_P90_BUDGET_MS
        else -> throw IllegalArgumentException("tier must be mid or low")
    }

    /** Parses a `pairs` argument such as `en-ur,hi-en`; `null` or blank means every pair in [Pairs]. */
    fun parsePairs(arg: String?): List<LangPair> {
        if (arg.isNullOrBlank()) return Pairs
        return arg.split(',').map(String::trim).filter(String::isNotEmpty).map { spec ->
            val parts = spec.split('-')
            require(parts.size == 2 && parts.all(String::isNotBlank)) { "pair must look like en-ur" }
            LangPair(Lang.of(parts[0]), Lang.of(parts[1]))
        }
    }

    /** `en-ur p50=48 p90=92 n=50`. */
    fun summaryLine(pair: LangPair, samplesMs: List<Long>): String =
        "${pair.source}-${pair.target} p50=${LatencyStats.percentile(samplesMs, 0.50)} " +
            "p90=${LatencyStats.percentile(samplesMs, 0.90)} n=${samplesMs.size}"

    fun withinBudget(samplesMs: List<Long>, budgetMs: Long): Boolean =
        LatencyStats.percentile(samplesMs, 0.90) <= budgetMs
}
