/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate.mlkit

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.analytics.PackKind
import com.manjul.aikeyboard.core.analytics.PackResult
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.translate.LangPair
import com.manjul.aikeyboard.core.translate.ModelSizes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The translation models (offline language packs). ML Kit is the source of truth (plan decision 4): [states] is an
 * in-memory mirror, refreshed on app resume, when the bar opens and when a download job finishes.
 *
 * A download is unique WorkManager work per language ([TranslateModelDownloadWorker], `translate-model-<lang>`),
 * owned by the app so it keeps going after the keyboard hides. Wi-Fi only unless the user turned that off. Only
 * user taps start one (the bar's "Download" chip, the Offline languages screen, the onboarding offer); nothing here
 * downloads on its own.
 *
 * ML Kit cannot cancel a download, so "Stop" (RN-06) hides the download at once, cancels the job if it has not
 * started, and otherwise marks the language in `translateStopped`: the job deletes the model when ML Kit finishes.
 * A flag whose job never ran (or was cut off) is cleaned up by the next [refresh].
 *
 * Language tags only: nothing here logs, and analytics get closed values.
 *
 * @param workManager resolved on first use, so creating the store does not initialise WorkManager.
 * @param scope runs settings writes and WorkManager calls started from the UI.
 */
class TranslateModelStore(
    private val models: RemoteModels,
    private val workManager: () -> WorkManager,
    private val settings: FeatureSettingsStore,
    private val analytics: (AnalyticsEvent) -> Unit,
    private val scope: CoroutineScope,
) : ModelPresence {
    private val _states = MutableStateFlow<Map<Lang, ModelUiState>>(emptyMap())

    /** Languages without an entry are [ModelUiState.ABSENT]. Empty until the first [refresh]. */
    val states: StateFlow<Map<Lang, ModelUiState>> = _states.asStateFlow()

    fun state(lang: Lang): ModelUiState = _states.value[lang] ?: ModelUiState.ABSENT

    /** The languages known to be on the device. */
    fun ready(): Set<Lang> = _states.value.filterValues { it == ModelUiState.READY }.keys

    /** Asks ML Kit (the engine re-checks before every translate) and updates [states] with the answer. */
    override suspend fun downloaded(): Set<Lang> = models.downloaded().also(::applyDownloaded)

    /**
     * Re-reads ML Kit's downloaded models and WorkManager's pending jobs (downloads survive a process restart).
     * Deletes models the user stopped whose job is gone. Initialises ML Kit and WorkManager: call it from the app
     * or after the user opened the bar, never at process start.
     */
    suspend fun refresh() {
        val downloaded = models.downloaded()
        val pending = runCatching { workManager().getWorkInfosByTagFlow(TAG).first() }
            .getOrDefault(emptyList())
            .filter { !it.state.isFinished }
            .mapNotNull(::langOf)
            .toSet()
        val stopped = settings.flow.first().translateStopped.map(Lang::of).toSet()
        val orphaned = stopped - pending
        val deleted = orphaned.filter { it in downloaded && runCatching { models.delete(it) }.isSuccess }.toSet()
        if (orphaned.isNotEmpty()) {
            val tags = orphaned.map { it.tag }.toSet()
            settings.update { it.copy(translateStopped = it.translateStopped - tags) }
        }
        _states.update { old ->
            buildMap {
                for ((lang, state) in old) if (state == ModelUiState.FAILED) put(lang, state)
                for (lang in pending - stopped) put(lang, ModelUiState.DOWNLOADING)
                // A stopped language whose job still runs is deleted by that job.
                for (lang in downloaded - deleted - (stopped intersect pending)) put(lang, ModelUiState.READY)
            }
        }
    }

    /** Download size still needed for [pair] (TRD §11.5 #5.1), from the last known state. */
    fun missingBytes(pair: LangPair): Long = ModelSizes.bytes(ModelSizes.missingFor(pair, ready()))

    /** Enqueues every language [pair] needs that is not on the device, including the English pivot. */
    fun download(pair: LangPair) {
        for (lang in linkedSetOf(pair.source, pair.target, Lang.English)) download(lang)
    }

    /** Enqueues one language. A language that is ready, downloading or unknown to ML Kit is left alone. */
    fun download(lang: Lang) {
        if (MlKitLanguages.toMlKit(lang) == null) return
        synchronized(_states) {
            val state = state(lang)
            if (state == ModelUiState.READY || state == ModelUiState.DOWNLOADING) return
            setState(lang, ModelUiState.DOWNLOADING)
        }
        val request = OneTimeWorkRequestBuilder<TranslateModelDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(
                        if (settings.current.translateWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED,
                    )
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setInputData(workDataOf(TranslateModelDownloadWorker.KEY_LANG to lang.tag))
            .addTag(TAG)
            .addTag(LANG_TAG_PREFIX + lang.tag)
            .build()
        scope.launch {
            // Downloading again after a Stop: the running job (kept by KEEP) must now keep the model.
            if (lang.tag in settings.flow.first().translateStopped) {
                settings.update { it.copy(translateStopped = it.translateStopped - lang.tag) }
            }
            workManager().enqueueUniqueWork(workName(lang), ExistingWorkPolicy.KEEP, request)
        }
    }

    /** "Stop" (RN-06). ML Kit cannot cancel a running download; its model is deleted when the job finishes. */
    fun stop(lang: Lang) {
        synchronized(_states) {
            if (state(lang) != ModelUiState.DOWNLOADING) return
            setState(lang, ModelUiState.ABSENT)
        }
        scope.launch {
            settings.update { it.copy(translateStopped = it.translateStopped + lang.tag) }
            val wm = workManager()
            val infos = runCatching { wm.getWorkInfosForUniqueWorkFlow(workName(lang)).first() }.getOrDefault(emptyList())
            if (infos.none { it.state == WorkInfo.State.RUNNING }) wm.cancelUniqueWork(workName(lang))
        }
    }

    /** True when [lang] may be deleted: English stays while another downloaded language needs it as the pivot. */
    fun canDelete(lang: Lang): Boolean = lang != Lang.English || ready().none { it != Lang.English }

    /** Deletes a downloaded model. Returns false when refused ([canDelete]) or when ML Kit failed. */
    suspend fun delete(lang: Lang): Boolean {
        if (!canDelete(lang)) return false
        try {
            models.delete(lang)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return false
        }
        setState(lang, ModelUiState.ABSENT)
        return true
    }

    /** One attempt of the download job for [lang]. Called by [TranslateModelDownloadWorker] only. */
    internal suspend fun runDownload(lang: Lang): ListenableWorker.Result {
        if (MlKitLanguages.toMlKit(lang) == null) return ListenableWorker.Result.failure()
        if (isStopped(lang)) {
            clearStopped(lang)
            setState(lang, ModelUiState.ABSENT)
            return ListenableWorker.Result.failure()
        }
        setState(lang, ModelUiState.DOWNLOADING)
        analytics(EventCatalog.packDownloadStart(lang.tag, PackKind.TRANSLATE, ModelSizes.APPROX_MODEL_BYTES))
        val result = try {
            models.download(lang, settings.current.translateWifiOnly)
            PackResult.OK
        } catch (e: CancellationException) {
            throw e // the system stopped the job (e.g. Wi-Fi lost); WorkManager runs it again
        } catch (_: Exception) {
            PackResult.FAILED
        }
        if (isStopped(lang)) {
            if (result == PackResult.OK) runCatching { models.delete(lang) }
            clearStopped(lang)
            setState(lang, ModelUiState.ABSENT)
            done(lang, PackResult.CANCELLED)
            return ListenableWorker.Result.failure()
        }
        setState(lang, if (result == PackResult.OK) ModelUiState.READY else ModelUiState.FAILED)
        done(lang, result)
        if (result == PackResult.OK) runCatching { downloaded() }
        return if (result == PackResult.OK) ListenableWorker.Result.success() else ListenableWorker.Result.failure()
    }

    internal fun workName(lang: Lang) = WORK_PREFIX + lang.tag

    private fun done(lang: Lang, result: PackResult) =
        analytics(EventCatalog.packDownloadDone(lang.tag, PackKind.TRANSLATE, ModelSizes.APPROX_MODEL_BYTES, result))

    private suspend fun isStopped(lang: Lang) = lang.tag in settings.flow.first().translateStopped

    private suspend fun clearStopped(lang: Lang) =
        settings.update { it.copy(translateStopped = it.translateStopped - lang.tag) }

    private fun setState(lang: Lang, state: ModelUiState) = _states.update {
        if (state == ModelUiState.ABSENT) it - lang else it + (lang to state)
    }

    /** A READY language ML Kit no longer has becomes ABSENT; a downloaded one becomes READY. */
    private fun applyDownloaded(downloaded: Set<Lang>) = _states.update { old ->
        val kept = old.filter { (lang, state) -> state != ModelUiState.READY || lang in downloaded }
        kept + downloaded.filter { kept[it] != ModelUiState.DOWNLOADING }.associateWith { ModelUiState.READY }
    }

    private fun langOf(info: WorkInfo): Lang? =
        info.tags.firstOrNull { it.startsWith(LANG_TAG_PREFIX) }?.let { Lang(it.removePrefix(LANG_TAG_PREFIX)) }

    companion object {
        /** Tag on every translation model job; the language is in a second `translate-model-lang:<tag>` tag. */
        const val TAG = "translate-model"
        private const val LANG_TAG_PREFIX = "translate-model-lang:"
        private const val WORK_PREFIX = "translate-model-"
    }
}
