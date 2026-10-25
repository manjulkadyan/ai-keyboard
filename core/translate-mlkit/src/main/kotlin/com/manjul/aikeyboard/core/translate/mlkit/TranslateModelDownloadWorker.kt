/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate.mlkit

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.manjul.aikeyboard.core.translate.Lang

/**
 * Downloads one translation model (see [TranslateModelStore.download]). The input data is the language tag only
 * (PC-6); nothing is logged. A language the user stopped meanwhile is deleted when ML Kit finishes (RN-06).
 */
class TranslateModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
    private val store: TranslateModelStore,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val tag = inputData.getString(KEY_LANG) ?: return Result.failure()
        return store.runDownload(Lang.of(tag))
    }

    companion object {
        const val KEY_LANG = "lang"
    }
}

/** Builds [TranslateModelDownloadWorker]s with the app's [TranslateModelStore]; other workers return null. */
class TranslateModelDownloadWorkerFactory(private val store: () -> TranslateModelStore) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = if (workerClassName == TranslateModelDownloadWorker::class.java.name) {
        TranslateModelDownloadWorker(appContext, workerParameters, store())
    } else {
        null
    }
}
