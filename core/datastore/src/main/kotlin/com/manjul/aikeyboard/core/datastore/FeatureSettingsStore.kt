/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.datastore

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.Serializer
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal const val SILENCE_MS_MIN = 2000
internal const val SILENCE_MS_MAX = 5000

internal object FeatureSettingsSerializer : Serializer<FeatureSettings> {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val defaultValue: FeatureSettings = FeatureSettings()

    override suspend fun readFrom(input: InputStream): FeatureSettings = try {
        json.decodeFromString(FeatureSettings.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        // Never put the file content in the message (it can contain package names).
        throw CorruptionException("feature_settings.json is not valid")
    }

    override suspend fun writeTo(t: FeatureSettings, output: OutputStream) {
        output.write(json.encodeToString(FeatureSettings.serializer(), t).encodeToByteArray())
    }
}

/**
 * Persists [FeatureSettings] in `files/datastore/feature_settings.json`. [current] is a cached value that
 * can be read synchronously from the IME main thread; it is [FeatureSettings] defaults until the first load
 * completes, and reflects every [update] immediately.
 */
class FeatureSettingsStore(
    context: Context,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val dataStore: DataStore<FeatureSettings> = MultiProcessDataStoreFactory.create(
        serializer = FeatureSettingsSerializer,
        corruptionHandler = ReplaceFileCorruptionHandler { FeatureSettingsSerializer.defaultValue },
        produceFile = { File(context.applicationContext.filesDir, "datastore/feature_settings.json") },
        scope = scope,
    )
    private val cached = MutableStateFlow(FeatureSettingsSerializer.defaultValue)

    init {
        scope.launch { dataStore.data.collect { cached.value = it } }
    }

    val flow: Flow<FeatureSettings> get() = dataStore.data

    val current: FeatureSettings get() = cached.value

    suspend fun update(transform: (FeatureSettings) -> FeatureSettings) {
        val written = dataStore.updateData { old ->
            val new = transform(old)
            new.copy(
                voiceSilenceMs = new.voiceSilenceMs.coerceIn(SILENCE_MS_MIN, SILENCE_MS_MAX),
                firstTranslateAt = new.firstTranslateAt.coerceAtLeast(0L),
                enableKeyboardCardShownAt = new.enableKeyboardCardShownAt.coerceAtLeast(0L),
                lastSourceLang = new.lastSourceLang?.takeIf { it.isNotBlank() },
            )
        }
        // Read-your-writes for [current]; the collector above catches up with the same value.
        cached.value = written
    }
}
