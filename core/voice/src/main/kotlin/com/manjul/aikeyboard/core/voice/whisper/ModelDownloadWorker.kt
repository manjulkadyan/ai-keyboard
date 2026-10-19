/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.voice.whisper

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters

/**
 * Downloads one Whisper model (see [WhisperModelManager.download]). The input names the model enum only;
 * the URL and paths come from the manager, and nothing is logged.
 */
class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
    private val manager: WhisperModelManager,
) : Worker(context, params) {
    override fun doWork(): Result {
        val name = inputData.getString(KEY_SPEC)
        val spec = WhisperModelSpec.entries.firstOrNull { it.name == name } ?: return Result.failure()
        return manager.runDownload(spec, id, runAttemptCount) { isStopped }
    }

    companion object {
        const val KEY_SPEC = "spec"
    }
}

/** Builds [ModelDownloadWorker]s with the app's [WhisperModelManager]; other workers fall back to the default. */
class ModelDownloadWorkerFactory(private val manager: () -> WhisperModelManager) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = if (workerClassName == ModelDownloadWorker::class.java.name) {
        ModelDownloadWorker(appContext, workerParameters, manager())
    } else {
        null
    }
}
