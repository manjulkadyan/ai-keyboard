/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.privacy

import android.content.Context
import com.manjul.aikeyboard.aiAppGraph
import com.manjul.aikeyboard.clipboardManager
import com.manjul.aikeyboard.core.database.TranslationHistoryRepository
import com.manjul.aikeyboard.core.database.UserWordRepository
import com.manjul.aikeyboard.core.datastore.FeatureSettings
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.core.firebase.FirebaseDataResetter
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.voice.whisper.WhisperModelSpec
import com.manjul.aikeyboard.featureSettingsStore
import com.manjul.aikeyboard.firebaseGraph
import com.manjul.aikeyboard.ime.clipboard.provider.ClipboardFileStorage
import com.manjul.aikeyboard.ime.clipboard.provider.ClipboardFilesDatabase
import com.manjul.aikeyboard.ime.clipboard.provider.ClipboardHistoryDatabase
import com.manjul.aikeyboard.ime.dictionary.DictionaryManager
import com.manjul.aikeyboard.translateModelStore
import com.manjul.aikeyboard.translationHistory
import com.manjul.aikeyboard.userWordRepository
import com.manjul.aikeyboard.whisperVoice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The on-device half of Delete my data and Clear data (S4 Task 12). [wipeLocal] clears:
 * - every Room table of `AppDatabase` (translation history, `user_word`, `user_bigram`) and the clipboard;
 * - [FeatureSettings], back to the defaults except `onboardingDone` (decision 20), the analytics opt-out and the
 *   signed entitlement cache (`pro*`), so a Pro user stays Pro until the next kb-api answer;
 * - the ad state (injected from `app.ads`, which owns `:core:ads`) and the in-memory AI LRU;
 * - the Whisper and translation models, only when asked;
 * - the Firebase installation id, Analytics app instance id and Auth user ([FirebaseResetter]).
 *
 * It keeps the Remote Config mirror (`rc_mirror.json` holds config only, no user data) and the upstream theme and
 * keyboard preferences. Each step runs even when an earlier one fails. Nothing here logs, and no step puts user
 * content in an exception.
 */
class DataWiper(
    private val settings: FeatureSettingsStore,
    private val history: TranslationHistoryRepository,
    private val userWords: UserWordRepository,
    private val clearClipboard: suspend () -> Unit,
    private val clearAdState: suspend () -> Unit,
    private val clearAiCache: suspend () -> Unit,
    private val deleteModels: suspend () -> Unit,
    private val firebase: FirebaseResetter,
    private val clearUpstreamDictionary: suspend () -> Unit = {},
) {
    /** Returns false when a step failed; the other steps still ran. */
    suspend fun wipeLocal(includeModels: Boolean): Boolean {
        var ok = true
        ok = step { history.clearAll(keepFavorites = false) } && ok
        ok = step { userWords.clearAll() } && ok
        ok = step(clearUpstreamDictionary) && ok
        ok = step(clearClipboard) && ok
        ok = step { settings.update(::resetKeepingEntitlement) } && ok
        ok = step(clearAdState) && ok
        ok = step(clearAiCache) && ok
        if (includeModels) ok = step(deleteModels) && ok
        ok = step { firebase.reset() } && ok
        return ok
    }

    private suspend fun step(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    companion object {
        /** The defaults, with onboarding, the analytics opt-out and the entitlement cache of [old]. */
        fun resetKeepingEntitlement(old: FeatureSettings): FeatureSettings = FeatureSettings(
            onboardingDone = old.onboardingDone,
            analyticsOptOut = old.analyticsOptOut,
            proActive = old.proActive,
            proState = old.proState,
            proExpiresAt = old.proExpiresAt,
            proIssuedAt = old.proIssuedAt,
            proSignature = old.proSignature,
            proCheckedAt = old.proCheckedAt,
        )

        /** The production wiper. [clearAdState] comes from `app.ads` through `Routes.kt`. */
        fun create(context: Context, clearAdState: suspend () -> Unit): DataWiper {
            val app = context.applicationContext
            return DataWiper(
                settings = app.featureSettingsStore().value,
                history = app.translationHistory().value,
                userWords = app.userWordRepository().value,
                clearClipboard = { clearClipboard(app) },
                clearAdState = clearAdState,
                clearAiCache = { app.aiAppGraph().let { if (it.isInitialized()) it.value.lru.clear() } },
                deleteModels = { deleteModels(app) },
                firebase = DefaultFirebaseResetter(FirebaseDataResetter(app, app.firebaseGraph().value.bootstrap)),
                clearUpstreamDictionary = {
                    withContext(Dispatchers.IO) { DictionaryManager.default().florisUserDictionaryDao()?.deleteAll() }
                },
            )
        }

        private suspend fun clearClipboard(app: Context) {
            // Clears the manager's in-memory list; its database write is async, so the table is cleared here too.
            app.clipboardManager().value.clearFullHistory()
            withContext(Dispatchers.IO) {
                val history = ClipboardHistoryDatabase.new(app)
                try {
                    history.clipboardItemDao().deleteAll()
                } finally {
                    history.close()
                }
                val files = ClipboardFilesDatabase.new(app)
                try {
                    files.clearAllTables()
                } finally {
                    files.close()
                }
                ClipboardFileStorage.resetClipboardFileStorage(app)
            }
        }

        private suspend fun deleteModels(app: Context) {
            val whisper = app.whisperVoice().value
            // The model manager may only be built when this build serves Whisper (see WhisperVoice).
            if (whisper.gate.anyEnabled) WhisperModelSpec.entries.forEach(whisper.models::delete)
            val store = app.translateModelStore().value
            store.refresh()
            // English is the pivot: it can be deleted only after every other language.
            val (english, others) = store.ready().partition { it == Lang.English }
            (others + english).forEach { store.delete(it) }
        }
    }
}
