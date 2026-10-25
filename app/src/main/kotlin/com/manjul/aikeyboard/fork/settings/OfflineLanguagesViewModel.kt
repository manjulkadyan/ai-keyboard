/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.settings

import android.content.Intent
import android.provider.Settings
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.core.translate.CloudTranslationEngine
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.translate.ModelSizes
import com.manjul.aikeyboard.core.translate.mlkit.MlKitLanguages
import com.manjul.aikeyboard.core.translate.mlkit.ModelUiState
import com.manjul.aikeyboard.core.translate.mlkit.TranslateModelStore
import com.manjul.aikeyboard.core.voice.whisper.ModelState
import com.manjul.aikeyboard.core.voice.whisper.WhisperModelSpec
import com.manjul.aikeyboard.fork.subtypes.MVP_LANGUAGE_TAGS
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One translation language. [cloudOnly] rows (e.g. Pashto) have no download: only the Pro cloud engine serves them. */
data class TranslateLanguageRow(
    val lang: Lang,
    val state: ModelUiState,
    val cloudOnly: Boolean = false,
    /** False for English while another downloaded language needs it as the pivot. */
    val deletable: Boolean = true,
    val bytes: Long = ModelSizes.APPROX_MODEL_BYTES,
)

data class OfflineLanguagesUiState(
    val languages: List<TranslateLanguageRow> = emptyList(),
    /** Approximate space the downloaded translation models take: READY count x the approximate model size. */
    val storageBytes: Long = 0L,
    val wifiOnly: Boolean = true,
    /** The Whisper models this device can run; empty when Whisper is not offered in this build. */
    val whisper: List<Pair<WhisperModelSpec, ModelState>> = emptyList(),
    /** Whether the platform has an on-device speech recognizer (its languages are managed in system settings). */
    val onDeviceRecognizer: Boolean = false,
)

/**
 * Offline languages (PRD FR-36): translation models from [store], the Whisper models and the system recognizer.
 * Every download starts from a tap here; nothing is downloaded on open. Language tags only.
 *
 * @param whisperStates the Whisper model manager's states, or null when Whisper is not offered (release builds).
 * @param catalog every language ML Kit translates.
 */
class OfflineLanguagesViewModel(
    private val store: TranslateModelStore,
    private val settings: FeatureSettingsStore,
    whisperStates: StateFlow<Map<WhisperModelSpec, ModelState>>?,
    whisperOffered: List<WhisperModelSpec>,
    onDeviceRecognizer: Boolean,
    private val scope: CoroutineScope,
    catalog: Set<Lang> = MlKitLanguages.all,
    displayLocale: Locale = Locale.getDefault(),
) {
    private val order: List<Lang> = languageOrder(catalog, displayLocale)

    val state: StateFlow<OfflineLanguagesUiState> = combine(
        store.states,
        settings.flow,
        whisperStates ?: flowOf(emptyMap()),
    ) { models, s, whisper ->
        val readyOthers = models.any { (lang, state) -> lang != Lang.English && state == ModelUiState.READY }
        OfflineLanguagesUiState(
            languages = order.map { lang ->
                TranslateLanguageRow(
                    lang = lang,
                    state = models[lang] ?: ModelUiState.ABSENT,
                    cloudOnly = lang in CloudTranslationEngine.CLOUD_ONLY_LANGS,
                    deletable = lang != Lang.English || !readyOthers,
                )
            },
            storageBytes = models.count { it.value == ModelUiState.READY } * ModelSizes.APPROX_MODEL_BYTES,
            wifiOnly = s.translateWifiOnly,
            whisper = whisperOffered.mapNotNull { spec -> whisper[spec]?.let { spec to it } },
            onDeviceRecognizer = onDeviceRecognizer,
        )
    }.stateIn(scope, SharingStarted.Eagerly, OfflineLanguagesUiState(onDeviceRecognizer = onDeviceRecognizer))

    /** Re-reads ML Kit's models when the screen resumes (plan decision 4). */
    fun refresh() {
        scope.launch { runCatching { store.refresh() } }
    }

    fun download(lang: Lang) = store.download(lang)

    /** RN-06: ML Kit cannot cancel, so the model is deleted once its download finishes. */
    fun stop(lang: Lang) = store.stop(lang)

    fun delete(lang: Lang) {
        scope.launch { store.delete(lang) }
    }

    /** One switch for every offline download on this screen: translation and Whisper models. */
    fun setWifiOnly(on: Boolean) {
        scope.launch { settings.update { it.copy(translateWifiOnly = on, whisperWifiOnly = on) } }
    }

    companion object {
        /** The system screen that manages the speech recognizer and its offline languages. */
        fun recognizerSettingsIntent(): Intent =
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** The MVP languages and Bengali first, then cloud-only languages, then the rest by display name. */
        fun languageOrder(catalog: Set<Lang>, displayLocale: Locale): List<Lang> {
            val first = (MVP_LANGUAGE_TAGS.map(Lang::of) + Lang("bn")).distinct().filter { it in catalog }
            val cloud = CloudTranslationEngine.CLOUD_ONLY_LANGS.filter { it !in catalog }.sortedBy { it.tag }
            val rest = (catalog - first.toSet())
                .sortedBy { Locale.forLanguageTag(it.tag).getDisplayName(displayLocale).lowercase(displayLocale) }
            return first + cloud + rest
        }
    }
}
