/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.phrasebook

import android.content.Context
import com.manjul.aikeyboard.appDatabase
import com.manjul.aikeyboard.core.content.phrasebook.PhraseTranslator
import com.manjul.aikeyboard.core.content.phrasebook.PhrasebookReader
import com.manjul.aikeyboard.core.content.phrasebook.PhrasebookRepository
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.translate.LangPair
import com.manjul.aikeyboard.core.translate.ModelSizes
import com.manjul.aikeyboard.core.translate.Origin
import com.manjul.aikeyboard.core.translate.TranslateRequest
import com.manjul.aikeyboard.core.translate.TranslateResult
import com.manjul.aikeyboard.core.translate.TranslationEngine
import com.manjul.aikeyboard.core.translate.mlkit.MlKitTranslationEngine
import com.manjul.aikeyboard.core.translate.mlkit.ModelUiState
import com.manjul.aikeyboard.translateModelStore
import com.manjul.aikeyboard.translation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The phrasebook objects (S5-phrasebook, PRD FR-22), built on first use. Translations come from the ML Kit engine
 * only, never through the engine router, so a phrase never reaches the cloud engine (FR-22 AC2). No ads here
 * (decision 19).
 */
class PhrasebookAppGraph(context: Context) {
    private val app = context.applicationContext
    private val lock = Mutex()
    private var repository: PhrasebookRepository? = null

    /** The repository over the `no_backup` copy of the bundled DB, or null when it cannot be opened. */
    suspend fun repository(): PhrasebookRepository? = lock.withLock {
        repository ?: withContext(Dispatchers.IO) {
            PhrasebookReader.open(app.noBackupFilesDir, ASSET_VERSION) { app.assets.open(ASSET) }
        }?.let { reader ->
            PhrasebookRepository(reader, app.appDatabase().value.phraseDao(), translator).also { repository = it }
        }
    }

    private val translator: PhraseTranslator by lazy {
        val core = app.translation().value
        mlKitPhraseTranslator(MlKitTranslationEngine(core.presence, core.pool, Dispatchers.IO))
    }

    /** Whether the `en -> target` models are on the device, now and on every change. */
    fun packReady(target: String): Flow<Boolean> {
        val pair = LangPair(Lang.English, Lang.of(target))
        return app.translateModelStore().value.states
            .map { states -> ModelSizes.missingFor(pair, states.filterValues { it == ModelUiState.READY }.keys) }
            .map { it.isEmpty() }
            .distinctUntilChanged()
    }

    /** Reads the downloaded models again, so [packReady] is current when the screen opens. */
    suspend fun refreshPacks() {
        runCatching { app.translateModelStore().value.refresh() }
    }

    /** "Download language": the Offline languages download of the `en -> target` models. */
    fun download(target: String) = app.translateModelStore().value.download(LangPair(Lang.English, Lang.of(target)))

    companion object {
        const val ASSET_VERSION = 1
        const val ASSET = "phrasebook/phrasebook-$ASSET_VERSION.db"
    }
}

/** A [PhraseTranslator] over an on-device [engine]: any failure, a missing pack included, gives null. */
internal fun mlKitPhraseTranslator(engine: TranslationEngine) = PhraseTranslator { text, source, target ->
    // Origin.TRANSLATOR: the request never leaves this adapter, and the engine does not log the text.
    val req = TranslateRequest(text, Lang.of(source), Lang.of(target), Origin.TRANSLATOR)
    (engine.translate(req) as? TranslateResult.Ok)?.text
}
