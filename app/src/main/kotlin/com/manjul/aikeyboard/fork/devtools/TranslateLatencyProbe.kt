/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.devtools

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.app.FlorisPreferenceStore
import com.manjul.aikeyboard.core.translate.CloudTranslationEngine
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.translate.LangPair
import com.manjul.aikeyboard.core.translate.LanguageDetector
import com.manjul.aikeyboard.core.translate.LatencyCheck
import com.manjul.aikeyboard.core.translate.Origin
import com.manjul.aikeyboard.core.translate.RouterContext
import com.manjul.aikeyboard.core.translate.SourceResolver
import com.manjul.aikeyboard.core.translate.TranslateRequest
import com.manjul.aikeyboard.core.translate.TranslateResult
import com.manjul.aikeyboard.core.translate.TranslationCache
import com.manjul.aikeyboard.core.translate.TranslationEngine
import com.manjul.aikeyboard.core.translate.TranslationEngineRouter
import com.manjul.aikeyboard.core.translate.mlkit.MlKitTranslationEngine
import com.manjul.aikeyboard.translation
import dev.patrickgold.jetpref.datastore.ui.Preference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.showLongToastSync
import org.florisboard.lib.compose.stringRes

/**
 * Debug-only devtools probe for the day-1 latency check (TRD §8, R-03; `docs/translation/latency.md`). Runs the
 * latency corpus through a [TranslationEngineRouter] over [engine], the same path the bar takes (model-presence
 * check, sentence chunking, engine timeout), with its own cache so repeats are never served from memory and the
 * app's LRU is untouched. Each pair gets [LatencyCheck.WARM_UP_CALLS] untimed calls, then one timed call per
 * corpus sentence.
 *
 * The result is plain numbers per pair (`en-ur p50=48 p90=92 n=50`), or `en-ur no_pack` when a call failed; no
 * source or translated text is kept, shown, logged or copied.
 *
 * @param corpus the sentences for a source language, or `null` when there is no corpus file.
 */
class TranslateLatencyProbe(
    engine: TranslationEngine,
    private val corpus: (Lang) -> List<String>?,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val cache = TranslationCache()
    private val router = TranslationEngineRouter(
        onDevice = engine,
        cloud = CloudTranslationEngine(),
        // The source is always explicit, so the detector is never asked.
        resolver = SourceResolver(NoDetector),
        cache = cache,
    )

    /** Measures every pair in [pairs] in order; [onPair] is called before each one. One result line per pair. */
    suspend fun run(pairs: List<LangPair> = LatencyCheck.Pairs, onPair: (LangPair) -> Unit = {}): List<String> =
        pairs.map { pair ->
            onPair(pair)
            measure(pair)
        }

    private suspend fun measure(pair: LangPair): String {
        val label = "${pair.source}-${pair.target}"
        val sentences = corpus(pair.source)
        if (sentences.isNullOrEmpty()) return "$label no_corpus"
        repeat(LatencyCheck.WARM_UP_CALLS) {
            val result = translate(sentences[it % sentences.size], pair)
            if (result is TranslateResult.Failed) return "$label ${result.kind.analytics}"
        }
        cache.clear()
        val samples = sentences.take(LatencyCheck.TIMED_CALLS).map { sentence ->
            val start = nanoTime()
            val result = translate(sentence, pair)
            if (result is TranslateResult.Failed) return "$label ${result.kind.analytics}"
            (nanoTime() - start) / 1_000_000
        }
        return LatencyCheck.summaryLine(pair, samples)
    }

    private suspend fun translate(text: String, pair: LangPair): TranslateResult =
        router.translate(TranslateRequest(text, pair.source, pair.target, Origin.BAR), ProbeContext, pair.source)

    private object NoDetector : LanguageDetector {
        override suspend fun identify(text: String): List<Pair<String, Float>> = emptyList()
    }

    companion object {
        /** On-device only: the probe never touches the cloud stub's route. */
        private val ProbeContext = RouterContext(
            allowTranslate = true,
            allowCloud = false,
            pro = false,
            cloudOptIn = false,
            preferQuality = false,
        )

        /**
         * The probe over the app's ML Kit engine pieces (model presence and translator pool). The corpus comes from
         * the debug APK's `latency/` assets (the instrumented test's corpus, added to debug builds only).
         */
        fun forApp(context: Context): TranslateLatencyProbe {
            val core = context.translation().value
            val engine = MlKitTranslationEngine(core.presence, core.pool, Dispatchers.IO)
            val assets = context.applicationContext.assets
            return TranslateLatencyProbe(engine, corpus = { lang ->
                try {
                    assets.open(LatencyCheck.corpusAsset(lang)).bufferedReader().use { LatencyCheck.parseCorpus(it.readText()) }
                } catch (_: java.io.IOException) {
                    null
                }
            })
        }

        /** Device line for the copied summary: model, API level and RAM tier inputs. No user data. */
        fun deviceLine(context: Context): String {
            val am = context.getSystemService(ActivityManager::class.java)
            val info = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
            val ramGiB = info.totalMem / (1024.0 * 1024 * 1024)
            return "device=${Build.MANUFACTURER} ${Build.MODEL} sdk=${Build.VERSION.SDK_INT} " +
                "ram=${"%.1f".format(java.util.Locale.ROOT, ramGiB)}GiB lowRam=${am?.isLowRamDevice ?: false}"
        }
    }
}

/** The devtools row (debug builds only): tap to measure; the summary shows the result and is copied. */
@Composable
fun TranslateLatencyProbeRow() {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var currentPair by remember { mutableStateOf<LangPair?>(null) }
    var result by remember { mutableStateOf<String?>(null) }

    val summary = currentPair?.let { stringRes(R.string.devtools__translate_latency__running, "pair" to "${it.source}-${it.target}") }
        ?: result
        ?: stringRes(R.string.devtools__translate_latency__summary)
    Preference(
        title = stringRes(R.string.devtools__translate_latency__label),
        summary = summary,
        onClick = {
            if (running) return@Preference
            running = true
            scope.launch {
                try {
                    val lines = withContext(Dispatchers.Default) {
                        TranslateLatencyProbe.forApp(context).run { pair -> currentPair = pair }
                    }
                    val text = (listOf(TranslateLatencyProbe.deviceLine(context)) + lines).joinToString("\n")
                    result = text
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("latency", text))
                    context.showLongToastSync(R.string.devtools__translate_latency__copied)
                } finally {
                    currentPair = null
                    running = false
                }
            }
        },
        enabledIf = { prefs.devtools.enabled isEqualTo true },
    )
}
