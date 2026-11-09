/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ads.cap

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.manjul.aikeyboard.core.ads.placement.AdFormat
import com.manjul.aikeyboard.core.ads.placement.AdPlacementId
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The ad session counters of plan decisions 14 and 15 (S4 Task 7). Times are wall-clock epoch millis; 0 means never.
 * [lastBackgroundAtMs] is the time the main Activity last stopped, so a return after 30 min starts a new session.
 */
data class AdSessionState(
    val sessionCount: Int = 0,
    val lastBackgroundAtMs: Long = 0,
    val featureUsesSinceInterstitial: Int = 0,
    val lastInterstitialAtMs: Long = 0,
    val lastAppOpenAtMs: Long = 0,
)

/** Everything in `ads_state.json`: the frequency-cap records and the session counters. No ad or user identifier. */
data class AdsState(
    val cap: AdCapState = AdCapState(),
    val session: AdSessionState = AdSessionState(),
)

/** The JSON form. Placement and format are strings, so a record from a newer or broken build is dropped, not fatal. */
@Serializable
private data class AdsStateJson(
    val shows: List<ShowJson> = emptyList(),
    @SerialName("session_count") val sessionCount: Int = 0,
    @SerialName("last_background_at") val lastBackgroundAt: Long = 0,
    @SerialName("feature_uses") val featureUses: Int = 0,
    @SerialName("last_interstitial_at") val lastInterstitialAt: Long = 0,
    @SerialName("last_app_open_at") val lastAppOpenAt: Long = 0,
)

@Serializable
private data class ShowJson(val placement: String, val format: String, val at: Long)

internal object AdsStateSerializer : Serializer<AdsState> {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val defaultValue: AdsState = AdsState()

    override suspend fun readFrom(input: InputStream): AdsState {
        val raw = try {
            json.decodeFromString(AdsStateJson.serializer(), input.readBytes().decodeToString())
        } catch (e: SerializationException) {
            // A fixed message: never the file content.
            throw CorruptionException("$FILE_NAME is not valid")
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("$FILE_NAME is not valid")
        }
        val records = raw.shows.mapNotNull { show ->
            val id = runCatching { AdPlacementId(show.placement) }.getOrNull() ?: return@mapNotNull null
            val format = AdFormat.entries.firstOrNull { it.name == show.format } ?: return@mapNotNull null
            AdShowRecord(id, format, show.at)
        }
        return AdsState(
            cap = AdCapState(records),
            session = AdSessionState(
                sessionCount = raw.sessionCount.coerceAtLeast(0),
                lastBackgroundAtMs = raw.lastBackgroundAt,
                featureUsesSinceInterstitial = raw.featureUses.coerceAtLeast(0),
                lastInterstitialAtMs = raw.lastInterstitialAt,
                lastAppOpenAtMs = raw.lastAppOpenAt,
            ),
        )
    }

    override suspend fun writeTo(t: AdsState, output: OutputStream) {
        val raw = AdsStateJson(
            shows = t.cap.records.map { ShowJson(it.placementId.value, it.format.name, it.shownAtEpochMillis) },
            sessionCount = t.session.sessionCount,
            lastBackgroundAt = t.session.lastBackgroundAtMs,
            featureUses = t.session.featureUsesSinceInterstitial,
            lastInterstitialAt = t.session.lastInterstitialAtMs,
            lastAppOpenAt = t.session.lastAppOpenAtMs,
        )
        output.write(json.encodeToString(AdsStateJson.serializer(), raw).encodeToByteArray())
    }

    const val FILE_NAME = "ads_state.json"
}

/**
 * [AdCapStateStore] on `datastore/ads_state.json` (TRD §4.11: caps are persisted, not per process). Only the app
 * process opens it; [create] still uses the multi-process factory, as [AdCapStateStore] requires, so a second
 * process can never corrupt it. The session counters live in the same file so one update covers both.
 */
class DataStoreAdCapStateStore(private val dataStore: DataStore<AdsState>) : AdCapStateStore {
    override suspend fun read(): AdCapState = dataStore.data.first().cap

    override suspend fun update(transform: (AdCapState) -> AdCapState): AdCapState =
        dataStore.updateData { it.copy(cap = transform(it.cap)) }.cap

    suspend fun readSession(): AdSessionState = dataStore.data.first().session

    suspend fun updateSession(transform: (AdSessionState) -> AdSessionState): AdSessionState =
        dataStore.updateData { it.copy(session = transform(it.session)) }.session

    companion object {
        const val FILE_NAME = AdsStateSerializer.FILE_NAME

        fun create(
            context: Context,
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        ): DataStoreAdCapStateStore = DataStoreAdCapStateStore(
            MultiProcessDataStoreFactory.create(
                serializer = AdsStateSerializer,
                corruptionHandler = ReplaceFileCorruptionHandler { AdsStateSerializer.defaultValue },
                produceFile = { File(context.applicationContext.filesDir, "datastore/$FILE_NAME") },
                scope = scope,
            ),
        )
    }
}
