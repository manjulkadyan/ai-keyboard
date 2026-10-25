/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translator

import android.content.Intent
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.database.TranslationHistoryDao
import com.manjul.aikeyboard.core.database.TranslationHistoryEntity
import com.manjul.aikeyboard.core.database.TranslationHistoryRepository
import com.manjul.aikeyboard.core.datastore.FeatureSettings
import com.manjul.aikeyboard.core.translate.EngineId
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.translate.LangPair
import com.manjul.aikeyboard.core.translate.TranslateRequest
import com.manjul.aikeyboard.core.translate.TranslateResult
import com.manjul.aikeyboard.core.translate.bar.DebouncePolicy
import com.manjul.aikeyboard.core.tts.TtsAvailability
import com.manjul.aikeyboard.core.tts.TtsState
import com.manjul.aikeyboard.fork.translate.TranslateModels
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Room's DAO over a list, with the same `LIKE` and ordering semantics for the cases the tests use. */
class FakeHistoryDao : TranslationHistoryDao() {
    val rows = MutableStateFlow<List<TranslationHistoryEntity>>(emptyList())
    private var nextId = 1L

    override fun observe(pattern: String?, favoritesOnly: Boolean, limit: Int): Flow<List<TranslationHistoryEntity>> {
        val needle = pattern?.removePrefix("%")?.removeSuffix("%")?.replace("\\", "")
        return rows.map { all ->
            all.filter { !favoritesOnly || it.isFavorite }
                .filter {
                    needle == null || it.sourceText.contains(needle, ignoreCase = true) ||
                        it.targetText.contains(needle, ignoreCase = true)
                }
                .sortedWith(compareByDescending<TranslationHistoryEntity> { it.createdAt }.thenByDescending { it.id })
                .take(limit)
        }
    }

    override suspend fun byId(id: Long) = rows.value.firstOrNull { it.id == id }

    override suspend fun insert(entity: TranslationHistoryEntity): Long {
        val id = nextId++
        rows.value = rows.value + entity.copy(id = id)
        return id
    }

    override suspend fun update(entity: TranslationHistoryEntity): Int {
        rows.value = rows.value.map { if (it.id == entity.id) entity else it }
        return 1
    }

    override suspend fun setFavorite(id: Long, favorite: Boolean) {
        rows.value = rows.value.map { if (it.id == id) it.copy(isFavorite = favorite) else it }
    }

    override suspend fun delete(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }

    override suspend fun deleteNonFavorites() {
        rows.value = rows.value.filter { it.isFavorite }
    }

    override suspend fun count() = rows.value.size

    override suspend fun deleteOldestNonFavorites(n: Int) {
        val drop = rows.value.filterNot { it.isFavorite }.sortedBy { it.createdAt }.take(n).map { it.id }.toSet()
        rows.value = rows.value.filterNot { it.id in drop }
    }

    override suspend fun insertAndTrim(entity: TranslationHistoryEntity, maxRows: Int): Long {
        val id = insert(entity)
        val excess = count() - maxRows
        if (excess > 0) deleteOldestNonFavorites(excess)
        return id
    }
}

class FakeTts : TranslatorTts {
    override val state = MutableStateFlow<TtsState>(TtsState.Idle)
    var availability = TtsAvailability.AVAILABLE
    val spoken = mutableListOf<String>()
    var stops = 0
    var acquired = 0

    override fun acquire() {
        acquired++
    }

    override fun release() {
        acquired--
    }

    override suspend fun availability(locale: Locale) = availability

    override fun speak(text: String, locale: Locale) {
        spoken += text
    }

    override fun stop() {
        stops++
    }

    override fun installVoiceIntent() = Intent("android.speech.tts.engine.INSTALL_TTS_DATA")
}

class FakeSettings(initial: FeatureSettings = FeatureSettings()) : TranslatorSettings {
    val state = MutableStateFlow(initial)
    override val flow: Flow<FeatureSettings> get() = state
    override val current: FeatureSettings get() = state.value

    override suspend fun update(transform: (FeatureSettings) -> FeatureSettings) {
        state.value = transform(state.value)
    }
}

class FakeMic : TranslatorMic {
    override val state = MutableStateFlow<MicState>(MicState.Idle)
    var onText: ((String, Boolean) -> Unit)? = null
    var lang: String? = null
    var stops = 0

    override fun start(lang: String, onText: (text: String, final: Boolean) -> Unit) {
        this.lang = lang
        this.onText = onText
        state.value = MicState.Listening
    }

    override fun stop() {
        stops++
        state.value = MicState.Idle
    }

    override fun destroy() {
        state.value = MicState.Idle
    }
}

class FakeModels(var ready: Set<Lang> = emptySet()) : TranslateModels {
    val downloads = mutableListOf<LangPair>()
    override fun downloaded() = ready
    override suspend fun refresh() = Unit
    override fun download(pair: LangPair) {
        downloads += pair
    }
}

/** Builds a view model over fakes; [results] answers each call in order (the last one repeats). */
class TranslatorRig(
    scope: CoroutineScope,
    settings: FeatureSettings = FeatureSettings(),
    val results: MutableList<TranslateResult> = mutableListOf(),
    val pair: LangPair = LangPair(Lang.English, Lang("es")),
    debounce: DebouncePolicy = DebouncePolicy.Translator,
) {
    val dao = FakeHistoryDao()
    val history = TranslationHistoryRepository(dao, clock = { now })
    val tts = FakeTts()
    val settings = FakeSettings(settings)
    val mic = FakeMic()
    val models = FakeModels()
    val events = mutableListOf<AnalyticsEvent>()
    val requests = mutableListOf<TranslateRequest>()
    var now = 1_000L

    val vm = TranslatorViewModel(
        router = { req, _ ->
            requests += req
            when {
                results.size > 1 -> results.removeAt(0)
                results.size == 1 -> results[0]
                else -> TranslateResult.Ok("<${req.text}>", req.source ?: Lang("ur"), EngineId.MLKIT)
            }
        },
        models = models,
        history = history,
        tts = tts,
        settings = this.settings,
        analytics = { events += it },
        clock = { now },
        speech = mic,
        scope = scope,
        defaultPair = { pair },
        debounce = debounce,
    )

    fun named(name: String) = events.filter { it.name == name }
}
