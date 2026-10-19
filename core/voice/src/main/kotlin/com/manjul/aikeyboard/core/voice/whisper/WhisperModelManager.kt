/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.voice.whisper

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.analytics.PackKind
import com.manjul.aikeyboard.core.analytics.PackResult
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ModelState { ABSENT, DOWNLOADING, READY, FAILED }

/**
 * Whisper model files in `<noBackupDir>/models/` (never backed up). A download is unique WorkManager work
 * per model ([ModelDownloadWorker]): the file is written to `<name>.part`, SHA-256 verified against the
 * pinned hash and only then renamed. A model whose pinned hash is empty is never downloaded (fails closed).
 *
 * [baseUrl] ends with "/" and the file name is appended. [scope] runs blocking WorkManager queries (an IO
 * scope in the app). Nothing here logs; [analytics] only gets closed values.
 */
class WhisperModelManager(
    noBackupDir: File,
    private val workManager: WorkManager,
    private val ramBytes: Long,
    private val baseUrl: String,
    private val pinnedSha256: (WhisperModelSpec) -> String = { it.sha256 },
    private val analytics: (AnalyticsEvent) -> Unit = {},
    scope: CoroutineScope,
) : WhisperModelSource {
    val modelsDir = File(noBackupDir, MODELS_DIR)

    private val _states = MutableStateFlow(
        WhisperModelSpec.entries.associateWith { if (file(it).isFile) ModelState.READY else ModelState.ABSENT },
    )
    val states: StateFlow<Map<WhisperModelSpec, ModelState>> = _states.asStateFlow()

    /** The request id of each model's current download; results of older (cancelled) requests are ignored. */
    private val requests = HashMap<WhisperModelSpec, UUID>()

    init {
        scope.launch { restoreDownloads() }
    }

    /** Models this device can run: [WhisperModelSpec.SMALL] needs 6 GiB of RAM. */
    fun offered(): List<WhisperModelSpec> = WhisperModelSpec.entries.filter { ramBytes >= it.minRamBytes }

    /** The largest downloaded model this device can run, or null. */
    fun readyModel(): WhisperModelSpec? =
        offered().filter { _states.value[it] == ModelState.READY }.maxByOrNull { it.bytes }

    override fun readyModelPath(): String? = readyModel()?.let { file(it).path }

    fun download(spec: WhisperModelSpec, wifiOnly: Boolean) {
        if (spec !in offered()) return
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setInputData(workDataOf(ModelDownloadWorker.KEY_SPEC to spec.name))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        synchronized(requests) {
            val state = _states.value[spec]
            if (state == ModelState.READY || state == ModelState.DOWNLOADING) return
            requests[spec] = request.id
            setState(spec, ModelState.DOWNLOADING)
        }
        analytics(EventCatalog.packDownloadStart(LANG, PackKind.WHISPER, spec.bytes))
        workManager.enqueueUniqueWork(workName(spec), ExistingWorkPolicy.KEEP, request)
    }

    /** Cancels a running download and deletes the model and any partial file. */
    fun delete(spec: WhisperModelSpec) {
        synchronized(requests) {
            requests.remove(spec)
            setState(spec, ModelState.ABSENT)
        }
        workManager.cancelUniqueWork(workName(spec))
        file(spec).delete()
        partFile(spec).delete()
    }

    internal fun workName(spec: WhisperModelSpec) = "whisper_model_" + spec.name.lowercase()

    private fun file(spec: WhisperModelSpec) = File(modelsDir, spec.fileName)

    private fun partFile(spec: WhisperModelSpec) = File(modelsDir, spec.fileName + PART_SUFFIX)

    private fun setState(spec: WhisperModelSpec, state: ModelState) = _states.update { it + (spec to state) }

    /** Marks downloads WorkManager still holds after a restart; drops partial files nobody will finish. */
    private fun restoreDownloads() {
        for (spec in WhisperModelSpec.entries) {
            val pending = runCatching { workManager.getWorkInfosForUniqueWork(workName(spec)).get() }
                .getOrDefault(emptyList())
                .firstOrNull { !it.state.isFinished }
            synchronized(requests) {
                if (pending != null && _states.value[spec] == ModelState.ABSENT) {
                    requests[spec] = pending.id
                    setState(spec, ModelState.DOWNLOADING)
                } else if (pending == null && _states.value[spec] != ModelState.DOWNLOADING) {
                    partFile(spec).delete()
                }
            }
        }
    }

    /**
     * Runs one download attempt for the worker with [requestId]. Blocking; [isStopped] is polled while
     * streaming. A system stop (constraints lost, time limit) keeps the partial file so the retry resumes it.
     */
    internal fun runDownload(
        spec: WhisperModelSpec,
        requestId: UUID,
        runAttempt: Int,
        isStopped: () -> Boolean,
    ): ListenableWorker.Result {
        adopt(spec, requestId)
        val expected = pinnedSha256(spec).lowercase()
        if (!SHA256_HEX.matches(expected)) return finish(spec, requestId, PackResult.HASH_MISMATCH)
        val part = partFile(spec)
        val outcome = try {
            modelsDir.mkdirs()
            if (modelsDir.usableSpace < spec.bytes + FREE_SPACE_MARGIN) {
                Outcome.Done(PackResult.FAILED)
            } else {
                fetch(spec, part, expected, isStopped)
            }
        } catch (e: IOException) {
            Outcome.Retry
        }
        return when (outcome) {
            Outcome.Stopped -> {
                if (isCurrent(spec, requestId)) return ListenableWorker.Result.retry()
                part.delete()
                finish(spec, requestId, PackResult.CANCELLED)
            }
            Outcome.Retry -> {
                if (runAttempt + 1 < MAX_ATTEMPTS && isCurrent(spec, requestId)) return ListenableWorker.Result.retry()
                part.delete()
                finish(spec, requestId, PackResult.FAILED)
            }
            is Outcome.Done -> {
                if (outcome.result != PackResult.OK) part.delete()
                finish(spec, requestId, outcome.result)
            }
        }
    }

    private sealed interface Outcome {
        data object Stopped : Outcome
        data object Retry : Outcome
        data class Done(val result: PackResult) : Outcome
    }

    private fun fetch(spec: WhisperModelSpec, part: File, expected: String, isStopped: () -> Boolean): Outcome {
        val digest = MessageDigest.getInstance("SHA-256")
        var have = if (part.isFile) part.length() else 0L
        val limit = spec.bytes * SIZE_CAP_FACTOR
        if (have > limit) {
            part.delete()
            have = 0L
        }
        val connection = URL(baseUrl + spec.fileName).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            if (have > 0) connection.setRequestProperty("Range", "bytes=$have-")
            val append = when (connection.responseCode) {
                HttpURLConnection.HTTP_PARTIAL -> have > 0
                HttpURLConnection.HTTP_OK -> false
                in SERVER_ERRORS -> throw IOException("server error")
                else -> return Outcome.Done(PackResult.FAILED)
            }
            if (append) {
                FileInputStream(part).use { input ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        digest.update(buffer, 0, n)
                    }
                }
            } else {
                have = 0L
            }
            FileOutputStream(part, append).use { out ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        if (isStopped()) return Outcome.Stopped
                        val n = input.read(buffer)
                        if (n < 0) break
                        have += n
                        if (have > limit) return Outcome.Done(PackResult.FAILED)
                        digest.update(buffer, 0, n)
                        out.write(buffer, 0, n)
                    }
                }
                out.fd.sync()
            }
        } finally {
            connection.disconnect()
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != expected) return Outcome.Done(PackResult.HASH_MISMATCH)
        val dest = file(spec)
        dest.delete()
        if (!part.renameTo(dest)) return Outcome.Done(PackResult.FAILED)
        return Outcome.Done(PackResult.OK)
    }

    private fun isCurrent(spec: WhisperModelSpec, requestId: UUID) =
        synchronized(requests) { requests[spec] == requestId }

    /** A download WorkManager resumed after a restart, before [restoreDownloads] saw it. */
    private fun adopt(spec: WhisperModelSpec, requestId: UUID) = synchronized(requests) {
        if (requests[spec] == null && _states.value[spec] != ModelState.READY) {
            requests[spec] = requestId
            setState(spec, ModelState.DOWNLOADING)
        }
    }

    private fun finish(spec: WhisperModelSpec, requestId: UUID, result: PackResult): ListenableWorker.Result {
        val current = synchronized(requests) {
            val current = requests[spec] == requestId
            if (current) {
                requests.remove(spec)
                val ready = result == PackResult.OK && file(spec).isFile
                setState(spec, if (ready) ModelState.READY else ModelState.FAILED)
            }
            current
        }
        if (current || result == PackResult.CANCELLED) {
            analytics(EventCatalog.packDownloadDone(LANG, PackKind.WHISPER, spec.bytes, result))
        }
        return if (result == PackResult.OK) ListenableWorker.Result.success() else ListenableWorker.Result.failure()
    }

    companion object {
        const val MODELS_DIR = "models"
        private const val PART_SUFFIX = ".part"

        /** Whisper models are multilingual. */
        private const val LANG = "mul"
        private val SHA256_HEX = Regex("^[0-9a-f]{64}$")
        private const val MAX_ATTEMPTS = 3
        private const val BACKOFF_SECONDS = 30L
        private const val TIMEOUT_MS = 30_000
        private const val BUFFER_BYTES = 64 * 1024
        private const val SIZE_CAP_FACTOR = 2
        private const val FREE_SPACE_MARGIN = 50_000_000L
        private val SERVER_ERRORS = 500..599
    }
}
