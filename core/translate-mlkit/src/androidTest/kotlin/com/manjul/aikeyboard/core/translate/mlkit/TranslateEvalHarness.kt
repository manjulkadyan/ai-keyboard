/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate.mlkit

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.translate.LangPair
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * ur/hi/ar/bn quality harness (plan Task 12, `docs/translation/quality-ur-hi-ar-bn.md`). Runs our own corpus
 * (`assets/eval/<lang>.tsv`: 200 English sources and 50 native-script sources per language) through ML Kit and
 * writes `eval-<lang>-<date>.csv` (id, direction, register, source, ML Kit output, latency ms, error) to the
 * test's external files dir, for pulling off the device and handing to native raters. It measures nothing
 * against a threshold; ratings decide the result.
 *
 * Needs a device. Models must already be downloaded, or pass `-e download true` to fetch missing ones
 * (any network). Optional `-e langs ur,hi` limits the languages. Status lines carry counts and paths
 * only; the corpus text goes to the CSV only.
 *
 * ```
 * adb shell am instrument -w -e class com.manjul.aikeyboard.core.translate.mlkit.TranslateEvalHarness \
 *   -e download true com.manjul.aikeyboard.core.translate.mlkit.test/androidx.test.runner.AndroidJUnitRunner
 * adb pull /sdcard/Android/data/com.manjul.aikeyboard.core.translate.mlkit.test/files/eval/
 * ```
 */
@RunWith(AndroidJUnit4::class)
class TranslateEvalHarness {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val args = InstrumentationRegistry.getArguments()

    @Test
    fun translateCorpusToCsv() = runBlocking {
        val langs = (args.getString(ARG_LANGS) ?: DEFAULT_LANGS).split(',').map { Lang.of(it) }.filter { it.tag.isNotEmpty() }
        val outDir = requireNotNull(context.getExternalFilesDir(OUTPUT_DIR)) { "no external files dir" }
        val date = LocalDate.now().toString()
        MlKitBootstrap.ensure(context)
        ensureModels(langs + Lang.English, download = args.getString(ARG_DOWNLOAD).toBoolean())

        for (lang in langs) {
            val rows = readCorpus(lang)
            val out = File(outDir, "eval-${lang.tag}-$date.csv")
            out.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(csvLine(listOf("id", "direction", "register", "source", "mlkit_output", "latency_ms", "error")))
                for ((direction, group) in rows.groupBy { it.direction }) {
                    val pair = LangPair(Lang.of(direction.substringBefore('-')), Lang.of(direction.substringAfter('-')))
                    val handle = mlKitTranslatorOpener(context)(pair)
                    try {
                        // Warm-up: the first calls load the model; they are not timed or written.
                        group.take(WARM_UP).forEach { runCatching { handle.translate(it.source) } }
                        for (row in group) {
                            val start = SystemClock.elapsedRealtimeNanos()
                            val result = runCatching { withTimeout(TIMEOUT_MS) { handle.translate(row.source) } }
                            val latencyMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000
                            val error = result.exceptionOrNull()?.let { MlKitErrors.kind(it).name }.orEmpty()
                            writer.write(
                                csvLine(
                                    listOf(
                                        row.id, row.direction, row.register, row.source,
                                        result.getOrDefault(""), if (error.isEmpty()) latencyMs.toString() else "", error,
                                    ),
                                ),
                            )
                        }
                    } finally {
                        handle.close()
                    }
                }
            }
            // Counts and the path only, shown in the `am instrument` output; never corpus text.
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "${lang.tag}: ${rows.size} rows -> ${out.absolutePath}\n") })
            assertTrue("${lang.tag}: empty corpus", rows.isNotEmpty())
        }
    }

    private suspend fun ensureModels(langs: List<Lang>, download: Boolean) {
        val manager = RemoteModelManager.getInstance()
        val present = manager.getDownloadedModels(TranslateRemoteModel::class.java).await().map { it.language }.toSet()
        val missing = langs.distinct().mapNotNull { MlKitLanguages.toMlKit(it) }.filter { it !in present }
        if (missing.isEmpty()) return
        check(download) { "models not downloaded: $missing (download them in the app or pass -e download true)" }
        val conditions = DownloadConditions.Builder().build()
        for (code in missing) {
            manager.download(TranslateRemoteModel.Builder(code).build(), conditions).await()
        }
    }

    private fun readCorpus(lang: Lang): List<Row> {
        val lines = instrumentation.context.assets.open("$OUTPUT_DIR/${lang.tag}.tsv").bufferedReader(Charsets.UTF_8).readLines()
        return lines.asSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .drop(1) // header
            .map { it.split('\t') }
            .map { Row(id = it[0], direction = it[1], register = it[2], source = it[3]) }
            .toList()
    }

    private data class Row(val id: String, val direction: String, val register: String, val source: String)

    private companion object {
        const val ARG_LANGS = "langs"
        const val ARG_DOWNLOAD = "download"
        const val DEFAULT_LANGS = "ur,hi,ar,bn"
        const val OUTPUT_DIR = "eval"
        const val WARM_UP = 3
        const val TIMEOUT_MS = 30_000L

        fun csvLine(fields: List<String>): String =
            fields.joinToString(",", postfix = "\n") { "\"" + it.replace("\"", "\"\"") + "\"" }
    }
}
