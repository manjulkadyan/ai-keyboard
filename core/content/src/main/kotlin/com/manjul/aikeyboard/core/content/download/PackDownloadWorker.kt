/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import com.manjul.aikeyboard.core.analytics.AnalyticsSink
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.analytics.PackKind
import com.manjul.aikeyboard.core.analytics.PackResult
import com.manjul.aikeyboard.core.common.Outcome
import com.manjul.aikeyboard.core.content.manifest.ManifestCache
import com.manjul.aikeyboard.core.content.manifest.PackKindSpec
import com.manjul.aikeyboard.core.content.manifest.PackSpec

/**
 * Downloads one pack of the current manifest. The input holds the pack id only: the paths and hashes come from the
 * signed manifest. It emits `pack_download_start` once per download (on the first attempt) and `pack_download_done`
 * once at the end, with the kind and size bucket, never a URL (plan F14). It makes at most [MAX_ATTEMPTS] attempts.
 * [onInstalled] lets the caller register the pack (for example the sticker tables).
 */
class PackDownloadWorker(
    context: Context,
    params: WorkerParameters,
    private val deps: Deps,
) : CoroutineWorker(context, params) {
    class Deps(
        val manifest: ManifestCache,
        val downloader: PackDownloader,
        val analytics: AnalyticsSink,
        val onInstalled: suspend (PackSpec, java.io.File) -> Unit = { _, _ -> },
    )

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_PACK_ID) ?: return Result.failure()
        val pack = deps.manifest.current()?.packs?.firstOrNull { it.id == id } ?: return Result.failure()
        val bytes = pack.files.sumOf { it.bytes }
        if (runAttemptCount == 0) deps.analytics.log(EventCatalog.packDownloadStart(lang(pack), kind(pack), bytes))
        return when (val outcome = deps.downloader.install(pack)) {
            is Outcome.Success -> {
                deps.onInstalled(pack, outcome.value)
                deps.analytics.log(EventCatalog.packDownloadDone(lang(pack), kind(pack), bytes, PackResult.OK))
                Result.success()
            }
            is Outcome.Failure -> {
                val mismatch = outcome.error is PackHashMismatchException
                val retry = !mismatch && shouldRetry(runAttemptCount)
                if (!retry) {
                    val result = if (mismatch) PackResult.HASH_MISMATCH else PackResult.FAILED
                    deps.analytics.log(EventCatalog.packDownloadDone(lang(pack), kind(pack), bytes, result))
                }
                if (retry) Result.retry() else Result.failure()
            }
        }
    }

    companion object {
        const val KEY_PACK_ID = "pack_id"
        const val MAX_ATTEMPTS = 3

        /** The tag of every pack download, so Delete my data can cancel them all. */
        const val TAG = "aikb-content-pack"

        fun uniqueName(packId: String) = "$TAG-$packId"

        /** [runAttemptCount] counts from 0, so attempts 0 and 1 retry and the third attempt is the last. */
        fun shouldRetry(runAttemptCount: Int) = runAttemptCount + 1 < MAX_ATTEMPTS

        /** Plan F14: dictionary packs report `en`, sticker packs `mul`. */
        fun lang(pack: PackSpec) = if (pack.kind == PackKindSpec.DICT) "en" else "mul"

        fun kind(pack: PackSpec) = if (pack.kind == PackKindSpec.DICT) PackKind.DICT else PackKind.STICKERS

        /** The download waits for an unmetered network while [wifiOnly] (`FeatureSettings.contentWifiOnly`). */
        fun constraints(wifiOnly: Boolean): Constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        /** Cancels every pending and running pack download (Delete my data), and waits until WorkManager took it. */
        suspend fun cancelAll(workManager: WorkManager) {
            workManager.cancelAllWorkByTag(TAG).await()
        }

        fun enqueue(context: Context, packId: String, wifiOnly: Boolean) {
            val request = OneTimeWorkRequestBuilder<PackDownloadWorker>()
                .setInputData(workDataOf(KEY_PACK_ID to packId))
                .setConstraints(constraints(wifiOnly))
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(uniqueName(packId), ExistingWorkPolicy.REPLACE, request)
        }
    }
}

/** Builds [PackDownloadWorker]s with the app's objects. Other workers fall back to the default. */
class PackDownloadWorkerFactory(private val deps: () -> PackDownloadWorker.Deps) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = if (workerClassName == PackDownloadWorker::class.java.name) {
        PackDownloadWorker(appContext, workerParameters, deps())
    } else {
        null
    }
}
