/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.firebase

import androidx.annotation.XmlRes
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.manjul.aikeyboard.core.common.NonFatalReporter
import com.manjul.aikeyboard.core.config.ConfigCatalog
import com.manjul.aikeyboard.core.datastore.RemoteConfigMirror
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Firebase Remote Config for the app process (S4 Task 1). It writes the activated values to [mirror], which the IME
 * reads; the IME never calls this class. Every call does nothing when Firebase is not configured.
 *
 * - [activateAndMirror] at a cold start activates the last fetched config and mirrors every catalog key.
 * - [fetchInBackground] after the first frame fetches for the next cold start (it does not activate).
 * - [listenForKillSwitches] while the app is started activates a live update at once, but mirrors only the
 *   kill-switch keys (decision 25).
 *
 * Every Firebase failure goes to [reporter] (class name and stack only) and leaves the mirror unchanged.
 */
class FirebaseRemoteConfigSource(
    private val bootstrap: FirebaseBootstrap,
    private val mirror: RemoteConfigMirror,
    private val debug: Boolean,
    @param:XmlRes private val defaultsXml: Int,
    private val reporter: NonFatalReporter,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val writeLock = Mutex()

    @Volatile private var instance: FirebaseRemoteConfig? = null

    private fun remoteConfig(): FirebaseRemoteConfig? {
        instance?.let { return it }
        val app = bootstrap.defaultApp() ?: return null
        return synchronized(this) {
            instance ?: FirebaseRemoteConfig.getInstance(app).also { rc ->
                rc.setConfigSettingsAsync(remoteConfigSettings { minimumFetchIntervalInSeconds = fetchInterval(debug) })
                rc.setDefaultsAsync(defaultsXml)
                instance = rc
            }
        }
    }

    suspend fun activateAndMirror() {
        guarded("rc_activate") {
            val rc = remoteConfig() ?: return@guarded
            rc.activate().await()
            writeMirror(rc, updatedKeys = null)
        }
    }

    fun fetchInBackground() {
        scope.launch { guarded("rc_fetch") { remoteConfig()?.fetch()?.await() } }
    }

    /** Listens for live updates until closed. Returns a no-op handle when Firebase is not configured. */
    fun listenForKillSwitches(): AutoCloseable {
        val rc = runCatching { remoteConfig() }.onFailure { reporter.report("rc_listen", it) }.getOrNull()
            ?: return AutoCloseable {}
        val registration = runCatching {
            rc.addOnConfigUpdateListener(
                object : ConfigUpdateListener {
                    override fun onUpdate(configUpdate: ConfigUpdate) {
                        val keys = configUpdate.updatedKeys.toSet()
                        if (keys.none { it in ConfigCatalog.killSwitches }) return
                        scope.launch {
                            guarded("rc_update") {
                                rc.activate().await()
                                writeMirror(rc, updatedKeys = keys)
                            }
                        }
                    }

                    override fun onError(error: FirebaseRemoteConfigException) {
                        reporter.report("rc_update", error)
                    }
                },
            )
        }.onFailure { reporter.report("rc_listen", it) }.getOrNull() ?: return AutoCloseable {}
        return AutoCloseable { runCatching { registration.remove() } }
    }

    private suspend fun writeMirror(rc: FirebaseRemoteConfig, updatedKeys: Set<String>?) {
        val activated = ConfigCatalog.all.associate { it.name to rc.getValue(it.name) }
            .filterValues { it.source != FirebaseRemoteConfig.VALUE_SOURCE_STATIC }
            .mapValues { (_, value) -> value.asString() }
        writeLock.withLock {
            mirror.replace(RcMirrorWriter.next(mirror.current, activated, updatedKeys))
        }
    }

    private suspend fun guarded(area: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            reporter.report(area, e)
        }
    }

    companion object {
        /** OD-18: 12 h in release, 1 h in debug. */
        fun fetchInterval(debug: Boolean): Long = if (debug) 3_600L else 43_200L
    }
}
