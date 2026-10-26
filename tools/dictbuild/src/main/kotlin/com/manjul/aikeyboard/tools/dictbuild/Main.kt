/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.tools.dictbuild

import com.manjul.aikeyboard.core.suggest.DictionaryFormat
import java.io.File

/**
 * Builds `<out>/<lang>.sdict` from `<wordlists>/<lang>/` (plan Task 2, decision 2):
 * read, [WordFilter], drop English leaks down to [MAX_ENGLISH_SHARE] ([EnglishLeak], every list but en), scale counts to log frequency bytes,
 * write. Urdu has no counts, so every Urdu word gets the flat frequency byte [UR_FREQUENCY].
 */
object Main {
    val LANGUAGES: List<String> = listOf("en", "es", "fr", "pt", "id", "ar", "hi", "ur")

    /** Synthetic count for the count-less Urdu list (passes the count filter, ranks by edit distance). */
    const val UR_FLAT_COUNT: Int = 3
    const val UR_FREQUENCY: Int = 16

    /** English share left in a non-English list; under PRD FR-05 AC2's 5 % with margin. */
    const val MAX_ENGLISH_SHARE: Double = 0.04

    /** Minimum shipped word count (plan: 20,000; 10,000 for ur; hi is capped by its source, see LICENSES.md). */
    fun minWords(lang: String): Int = when (lang) {
        "ur" -> 10_000
        "hi" -> 9_000
        else -> 20_000
    }

    /** Raw counts from the single list file in [dir]: `word count` lines, or bare words for ur. */
    fun readCounts(dir: File, lang: String): Map<String, Int> {
        val file = dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") }?.singleOrNull()
            ?: throw IllegalArgumentException("Expected exactly one .txt list for $lang")
        val out = HashMap<String, Int>()
        file.useLines { lines ->
            for (raw in lines) {
                val line = raw.trim().removePrefix("﻿")
                if (line.isEmpty()) continue
                if (lang == "ur") {
                    out[line] = UR_FLAT_COUNT
                    continue
                }
                val sp = line.lastIndexOf(' ')
                if (sp <= 0) continue
                val count = line.substring(sp + 1).toIntOrNull() ?: continue
                val word = line.substring(0, sp)
                out[word] = (out[word] ?: 0) + count
            }
        }
        return out
    }

    /** The (word, frequency byte) entries for [lang]. */
    fun build(wordlists: File, lang: String, en: Map<String, Int>): List<Pair<String, Int>> {
        val filtered = readCounts(File(wordlists, lang), lang).filter { (w, c) -> WordFilter.keep(lang, w, c) }
        val cleaned = if (lang == "en") filtered else EnglishLeak.removeLeaks(filtered, en, MAX_ENGLISH_SHARE)
        if (lang == "ur") return cleaned.keys.map { it to UR_FREQUENCY }
        val max = cleaned.values.maxOrNull() ?: 0
        return cleaned.map { (w, c) -> w to DictionaryFormat.scaleCount(c, max) }
    }

    fun run(wordlists: File, outDir: File) {
        outDir.mkdirs()
        val en = readCounts(File(wordlists, "en"), "en")
        for (lang in LANGUAGES) {
            val entries = build(wordlists, lang, en)
            val file = File(outDir, "$lang.sdict")
            file.outputStream().use { DictionaryFormat.writeScaled(it, lang, entries) }
            println("$lang.sdict words=${entries.size} bytes=${file.length()}")
        }
    }
}

fun main(args: Array<String>) {
    require(args.size == 2) { "usage: dictbuild <wordlists dir> <out dir>" }
    Main.run(File(args[0]), File(args[1]))
}
