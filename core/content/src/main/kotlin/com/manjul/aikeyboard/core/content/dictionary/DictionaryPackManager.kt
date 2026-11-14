/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.dictionary

import com.manjul.aikeyboard.core.content.download.PackDownloader
import com.manjul.aikeyboard.core.content.manifest.ManifestCache
import com.manjul.aikeyboard.core.content.manifest.PackKindSpec
import com.manjul.aikeyboard.core.content.manifest.PackSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The state of the dictionary pack row in Offline languages (FR-36). */
sealed interface PackState {
    data object NotInstalled : PackState

    /** WorkManager reports no byte progress, so [progress] is null until it can. */
    data class Downloading(val progress: Float? = null) : PackState

    data class Installed(val version: Int) : PackState

    /** No verified manifest, or it lists no dictionary pack. */
    data object Unavailable : PackState
}

/** Runs pack downloads in the background. Production uses WorkManager unique work per pack id. */
interface PackWork {
    fun enqueue(packId: String)

    fun cancel(packId: String)

    fun running(packId: String): Flow<Boolean>
}

/**
 * Installs and removes the dictionary pack (the manifest pack with kind `dict`). The installed file is
 * `root/dict/<id>/<version>/<file>`. It never logs; the worker emits `pack_download_*` (plan F14).
 */
class DictionaryPackManager(
    private val manifest: ManifestCache,
    private val downloader: PackDownloader,
    private val root: File,
    private val work: PackWork,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<PackState>(PackState.NotInstalled)
    val state: StateFlow<PackState> = _state.asStateFlow()

    @Volatile private var pack: PackSpec? = null
    private var watch: Job? = null

    /** Reads the manifest and the installed files, and follows a running download. */
    suspend fun refresh() {
        val spec = try {
            manifest.current()?.packs?.firstOrNull { it.kind == PackKindSpec.DICT }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        pack = spec
        if (spec == null) {
            // An installed pack still works offline when the manifest is gone.
            _state.value = scan()?.let { PackState.Installed(it.second) } ?: PackState.Unavailable
            return
        }
        _state.value = installedState()
        follow(spec.id)
    }

    fun install() {
        val spec = pack ?: return
        _state.value = PackState.Downloading()
        work.enqueue(spec.id)
    }

    fun remove() {
        val spec = pack
        scope.launch {
            val ids = listOfNotNull(spec?.id) + installedIds()
            ids.distinct().forEach { id ->
                work.cancel(id)
                downloader.remove(id)
            }
            _state.value = if (spec == null) PackState.Unavailable else PackState.NotInstalled
        }
    }

    /** The installed pack database, or null. */
    fun installedFile(): File? = scan()?.first

    private fun follow(packId: String) {
        watch?.cancel()
        watch = scope.launch {
            var wasRunning = false
            work.running(packId).collect { running ->
                if (running) {
                    _state.value = PackState.Downloading()
                } else if (wasRunning) {
                    _state.value = withContext(Dispatchers.IO) { installedState() }
                }
                wasRunning = running
            }
        }
    }

    private fun installedState(): PackState = scan()?.let { PackState.Installed(it.second) } ?: PackState.NotInstalled

    private fun installedIds(): List<String> =
        File(root, PackKindSpec.DICT.value).listFiles()?.filter { it.isDirectory }?.map { it.name }.orEmpty()

    /** The newest installed `.db` file and its version, preferring the manifest pack id. */
    private fun scan(): Pair<File, Int>? {
        val dictDir = File(root, PackKindSpec.DICT.value)
        val ids = listOfNotNull(pack?.id) + installedIds()
        for (id in ids.distinct()) {
            val versions = File(dictDir, id).listFiles()
                ?.filter { it.isDirectory && it.name.toIntOrNull() != null }
                ?.sortedByDescending { it.name.toInt() }
                .orEmpty()
            for (dir in versions) {
                val db = dir.listFiles()?.firstOrNull { it.isFile && it.name.endsWith(".db") } ?: continue
                return db to dir.name.toInt()
            }
        }
        return null
    }
}
