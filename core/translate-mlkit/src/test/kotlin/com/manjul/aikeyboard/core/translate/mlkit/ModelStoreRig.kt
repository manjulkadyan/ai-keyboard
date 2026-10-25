/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate.mlkit

import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.datastore.FeatureSettings
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.core.translate.Lang
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

/** ML Kit's model manager, in memory. */
class FakeRemoteModels(present: Set<Lang> = emptySet()) : RemoteModels {
    val present: MutableSet<Lang> = Collections.synchronizedSet(present.toMutableSet())
    val downloads: MutableList<Pair<Lang, Boolean>> = Collections.synchronizedList(mutableListOf())
    val deletes: MutableList<Lang> = Collections.synchronizedList(mutableListOf())
    var failWith: Exception? = null
    var onDownload: suspend (Lang) -> Unit = {}

    override suspend fun downloaded(): Set<Lang> = synchronized(present) { present.toSet() }

    override suspend fun download(lang: Lang, wifiOnly: Boolean) {
        downloads += lang to wifiOnly
        onDownload(lang)
        failWith?.let { throw it }
        present += lang
    }

    override suspend fun delete(lang: Lang) {
        deletes += lang
        present -= lang
    }
}

/** A [TranslateModelStore] over the test WorkManager; jobs wait until [runJob] meets their constraints. */
class ModelStoreRig(private val context: Context, val models: FakeRemoteModels = FakeRemoteModels()) {
    val events: MutableList<AnalyticsEvent> = Collections.synchronizedList(mutableListOf())
    val workerParams: MutableList<WorkerParameters> = Collections.synchronizedList(mutableListOf())
    val settings = FeatureSettingsStore(context, Dispatchers.Unconfined)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    lateinit var store: TranslateModelStore
    val workManager: WorkManager

    init {
        val factory = TranslateModelDownloadWorkerFactory { store }
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .setTaskExecutor(SynchronousExecutor())
                .setWorkerFactory(object : WorkerFactory() {
                    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                        factory.createWorker(appContext, workerClassName, workerParameters)
                            ?.also { workerParams += workerParameters }
                })
                .build(),
        )
        workManager = WorkManager.getInstance(context)
        store = TranslateModelStore(models, { workManager }, settings, { events += it }, scope)
    }

    fun jobs(lang: Lang): List<WorkInfo> = workManager.getWorkInfosForUniqueWork(store.workName(lang)).get()

    /** Runs the pending job of [lang] (the test driver stands in for Wi-Fi) and waits until it finishes. */
    fun runJob(lang: Lang): WorkInfo {
        val id = jobs(lang).single { !it.state.isFinished }.id
        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(id)
        val deadline = System.currentTimeMillis() + 10_000
        while (true) {
            val info = workManager.getWorkInfoById(id).get()!!
            if (info.state.isFinished) return info
            check(System.currentTimeMillis() < deadline) { "job did not finish" }
            Thread.sleep(10)
        }
    }

    fun updateSettings(transform: (FeatureSettings) -> FeatureSettings) =
        runBlocking { settings.update(transform) }

    fun named(name: String) = synchronized(events) { events.filter { it.name == name } }
}
