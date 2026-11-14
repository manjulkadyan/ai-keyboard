/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.dictionary

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.manjul.aikeyboard.aiAppGraph
import com.manjul.aikeyboard.appContext
import com.manjul.aikeyboard.appDatabase
import com.manjul.aikeyboard.contentManifest
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.AnalyticsSink
import com.manjul.aikeyboard.core.content.ContentDirs
import com.manjul.aikeyboard.core.content.dictionary.DictionaryPackManager
import com.manjul.aikeyboard.core.content.dictionary.DictionaryPackReader
import com.manjul.aikeyboard.core.content.dictionary.DictionaryRepository
import com.manjul.aikeyboard.core.content.dictionary.PackWork
import com.manjul.aikeyboard.core.content.download.PackDownloadWorker
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.featureSettingsStore
import com.manjul.aikeyboard.fork.voice.isOnline
import com.manjul.aikeyboard.packDownloader
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The dictionary objects (S5-dict, PRD FR-21), built on first use. The keyboard card reads only
 * [DictionaryRepository.lookupOffline]; the app screen and "Define" may also use kb-api. No ads or billing code here:
 * the IME loads this class (plan F8).
 */
class DictionaryAppGraph(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val settings: FeatureSettingsStore get() = app.featureSettingsStore().value

    val packManager: DictionaryPackManager by lazy {
        DictionaryPackManager(
            manifest = app.contentManifest().value,
            downloader = app.packDownloader().value,
            root = ContentDirs.packs(app),
            work = WorkManagerPackWork(app) { settings.current.contentWifiOnly },
            scope = scope,
        )
    }

    val repository: DictionaryRepository by lazy {
        DictionaryRepository(
            reader = readers::current,
            release = readers::release,
            dao = app.appDatabase().value.dictionaryDao(),
            // Created only when an app lookup needs kb-api; the keyboard card never gets here.
            client = { app.aiAppGraph().value.dictionary.value },
            settings = settings,
            analytics = RelaySink(app),
            clock = System::currentTimeMillis,
        )
    }

    private val readers = PackReaders { packManager.installedFile() }

    /** Whether a network is up, now and on every change (FR-21 AC2). */
    fun connectivity(): Flow<Boolean> = callbackFlow {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        trySend(isOnline(app))
        if (cm == null) {
            awaitClose()
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(isOnline(app))
            }

            override fun onLost(network: Network) {
                trySend(isOnline(app))
            }
        }
        val registered = runCatching { cm.registerDefaultNetworkCallback(callback) }.isSuccess
        awaitClose { if (registered) runCatching { cm.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()

    /** Events go through the batching relay (opt-out, field policy). Only two booleans, never the word. */
    private class RelaySink(private val app: Context) : AnalyticsSink {
        override fun log(event: AnalyticsEvent) = app.appContext().value.analytics.value.log(event)
    }
}

/**
 * Keeps one open reader for the installed pack file and reopens it when the file changes or goes away. Each [current]
 * is a lease that the caller ends with [release]. A reader that is swapped out closes at its last release, so a
 * lookup that is still running never reads a closed database.
 */
internal class PackReaders(private val installed: () -> File?) {
    private var open: Pair<File, DictionaryPackReader>? = null
    private val leases = HashMap<DictionaryPackReader, Int>()

    @Synchronized
    fun current(): DictionaryPackReader? {
        val file = installed()
        val cached = open
        if (cached != null && file != null && cached.first == file && file.isFile) return lease(cached.second)
        open = null
        cached?.second?.let { if (!leases.containsKey(it)) it.close() }
        file ?: return null
        val reader = DictionaryPackReader.open(file) ?: return null
        open = file to reader
        return lease(reader)
    }

    @Synchronized
    fun release(reader: DictionaryPackReader) {
        val count = leases[reader] ?: return
        if (count > 1) {
            leases[reader] = count - 1
            return
        }
        leases.remove(reader)
        if (open?.second !== reader) reader.close()
    }

    private fun lease(reader: DictionaryPackReader): DictionaryPackReader {
        leases[reader] = (leases[reader] ?: 0) + 1
        return reader
    }
}

/** Pack downloads as WorkManager unique work per pack id ([PackDownloadWorker]). */
private class WorkManagerPackWork(private val app: Context, private val wifiOnly: () -> Boolean) : PackWork {
    private val workManager get() = WorkManager.getInstance(app)

    override fun enqueue(packId: String) = PackDownloadWorker.enqueue(app, packId, wifiOnly())

    override fun cancel(packId: String) {
        workManager.cancelUniqueWork(PackDownloadWorker.uniqueName(packId))
    }

    override fun running(packId: String): Flow<Boolean> =
        workManager.getWorkInfosForUniqueWorkFlow(PackDownloadWorker.uniqueName(packId))
            .map { infos -> infos.any { !it.state.isFinished && it.state != WorkInfo.State.CANCELLED } }
            .distinctUntilChanged()
}
