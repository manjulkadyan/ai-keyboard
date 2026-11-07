/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.datastore

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The activated Remote Config values, as strings, and when they were written (epoch ms). */
@Serializable
data class RcMirrorState(
    val values: Map<String, String> = emptyMap(),
    val activatedAt: Long = 0L,
)

internal object RcMirrorSerializer : Serializer<RcMirrorState> {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val defaultValue: RcMirrorState = RcMirrorState()

    override suspend fun readFrom(input: InputStream): RcMirrorState = try {
        json.decodeFromString(RcMirrorState.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        // Never put the file content in the message (it holds package names).
        throw CorruptionException("rc_mirror.json is not valid")
    }

    override suspend fun writeTo(t: RcMirrorState, output: OutputStream) {
        output.write(json.encodeToString(RcMirrorState.serializer(), t).encodeToByteArray())
    }
}

/**
 * The IME's copy of the activated Firebase Remote Config, in `files/datastore/rc_mirror.json` (S4 Task 1). Only the
 * app process writes it (`FirebaseRemoteConfigSource`); the IME reads [current] and never talks to Firebase.
 * [current] is cached for synchronous reads from the IME main thread. It is empty until the first load completes,
 * so readers fall back to the catalog defaults.
 */
class RemoteConfigMirror(
    context: Context,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val dataStore: DataStore<RcMirrorState> = MultiProcessDataStoreFactory.create(
        serializer = RcMirrorSerializer,
        corruptionHandler = ReplaceFileCorruptionHandler { RcMirrorSerializer.defaultValue },
        produceFile = { File(context.applicationContext.filesDir, "datastore/$FILE_NAME") },
        scope = scope,
    )
    private val cached = MutableStateFlow(RcMirrorSerializer.defaultValue)

    init {
        scope.launch { dataStore.data.collect { cached.value = it } }
    }

    val current: Map<String, String> get() = cached.value.values

    val activatedAt: Long get() = cached.value.activatedAt

    /** Waits for the first read of the file, so [current] holds the stored values. */
    suspend fun awaitLoaded() {
        cached.value = dataStore.data.first()
    }

    suspend fun replace(values: Map<String, String>) {
        val written = dataStore.updateData { RcMirrorState(values = values, activatedAt = clock()) }
        cached.value = written
    }

    companion object {
        const val FILE_NAME = "rc_mirror.json"
    }
}
