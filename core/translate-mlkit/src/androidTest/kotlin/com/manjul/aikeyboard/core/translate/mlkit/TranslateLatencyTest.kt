/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate.mlkit

import android.app.Instrumentation
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.translate.LangPair
import com.manjul.aikeyboard.core.translate.LatencyCheck
import com.manjul.aikeyboard.core.translate.ModelSizes
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Day-1 ML Kit latency check (TRD §8, §9 R-03; method and decision rule in `docs/translation/latency.md`).
 *
 * Per pair: open one translator, make [LatencyCheck.WARM_UP_CALLS] untimed calls, then one timed `translate`
 * per corpus sentence ([LatencyCheck.TIMED_CALLS]) on the loaded translator, and report `pair p50 p90` lines.
 * The numbers are ML Kit inference time only; the devtools probe measures the full router path.
 *
 * Instrumentation arguments (`-e name value`):
 * - `tier`: `mid` asserts p90 <= 150 ms, `low` asserts p90 <= 400 ms; unset only measures.
 * - `pairs`: e.g. `en-ur,hi-en`; default every pair in [LatencyCheck.Pairs].
 * - `download`: `true` downloads missing models first (any network). Otherwise missing models fail the test.
 *
 * Output lines (also in logcat, tag `TranslateLatency`) carry pair tags and milliseconds only, never text.
 */
@RunWith(AndroidJUnit4::class)
class TranslateLatencyTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val args = InstrumentationRegistry.getArguments()
    private val appContext = instrumentation.targetContext

    @Test
    fun translateLatencyPerPair() = runBlocking {
        val budgetMs = LatencyCheck.p90BudgetMs(args.getString("tier"))
        val pairs = LatencyCheck.parsePairs(args.getString("pairs"))
        ensureModels(pairs, download = args.getString("download") == "true")

        val open = mlKitTranslatorOpener(appContext)
        val overBudget = mutableListOf<String>()
        for (pair in pairs) {
            val corpus = corpus(pair.source)
            val samples = open(pair).use { translator ->
                repeat(LatencyCheck.WARM_UP_CALLS) { translator.translate(corpus[it % corpus.size]) }
                corpus.take(LatencyCheck.TIMED_CALLS).map { sentence ->
                    val start = SystemClock.elapsedRealtimeNanos()
                    translator.translate(sentence)
                    (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000
                }
            }
            val line = LatencyCheck.summaryLine(pair, samples)
            report(line)
            if (budgetMs != null && !LatencyCheck.withinBudget(samples, budgetMs)) overBudget += line
        }
        assertTrue("p90 over ${budgetMs}ms: $overBudget", overBudget.isEmpty())
    }

    private suspend fun ensureModels(pairs: List<LangPair>, download: Boolean) {
        val downloaded = RemoteModelPresence(appContext).downloaded()
        val missing = pairs.flatMap { ModelSizes.missingFor(it, downloaded) }.toSet()
        if (missing.isEmpty()) return
        check(download) { "models missing for $missing; download them or pass -e download true" }
        val manager = RemoteModelManager.getInstance()
        for (lang in missing) {
            val code = requireNotNull(MlKitLanguages.toMlKit(lang)) { "unsupported $lang" }
            manager.download(TranslateRemoteModel.Builder(code).build(), DownloadConditions.Builder().build()).await()
        }
    }

    private fun corpus(lang: Lang): List<String> {
        val text = instrumentation.context.assets.open(LatencyCheck.corpusAsset(lang)).bufferedReader().use { it.readText() }
        val sentences = LatencyCheck.parseCorpus(text)
        check(sentences.size >= LatencyCheck.TIMED_CALLS) { "corpus for $lang has ${sentences.size} sentences" }
        return sentences
    }

    private fun report(line: String) {
        Log.i(TAG, line)
        instrumentation.sendStatus(0, Bundle().apply { putString(Instrumentation.REPORT_KEY_STREAMRESULT, "$line\n") })
    }

    private companion object {
        const val TAG = "TranslateLatency"
    }
}
