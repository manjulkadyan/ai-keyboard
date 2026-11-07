/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.billing

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Finishes a purchase verify that failed because kb-api was out of reach, also after a process restart.
 * It has no input data: it re-reads the purchases from Play, so no token is stored in WorkManager's database.
 */
class VerifyPurchaseWorker(
    context: Context,
    params: WorkerParameters,
    private val repository: EntitlementRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when {
        repository.syncFromWorker() -> Result.success()
        runAttemptCount >= MAX_ATTEMPTS -> Result.failure()
        else -> Result.retry()
    }

    companion object {
        const val UNIQUE_NAME = "aikb-verify-purchase"
        const val MAX_ATTEMPTS = 10
    }
}

/** Builds [VerifyPurchaseWorker]s with the app's repository. Other workers fall back to the default. */
class VerifyPurchaseWorkerFactory(private val repository: () -> EntitlementRepository) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = if (workerClassName == VerifyPurchaseWorker::class.java.name) {
        VerifyPurchaseWorker(appContext, workerParameters, repository())
    } else {
        null
    }
}

/** One unique [VerifyPurchaseWorker], network constrained, with exponential backoff from 30 s. */
class WorkManagerVerifyScheduler(private val context: Context) : VerifyRetryScheduler {
    override fun schedule() {
        val request = OneTimeWorkRequestBuilder<VerifyPurchaseWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(INITIAL_DELAY_S, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_S, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(VerifyPurchaseWorker.UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(VerifyPurchaseWorker.UNIQUE_NAME)
    }

    private companion object {
        /** The in-process retries cover the first 15 min. */
        const val INITIAL_DELAY_S = 15 * 60L
        const val BACKOFF_S = 30L
    }
}
