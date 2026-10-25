/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translate

import android.content.Context
import com.manjul.aikeyboard.core.translate.CloudTranslationEngine
import com.manjul.aikeyboard.core.translate.LangPair
import com.manjul.aikeyboard.core.translate.SourceResolver
import com.manjul.aikeyboard.core.translate.TranslationCache
import com.manjul.aikeyboard.core.translate.TranslationEngineRouter
import com.manjul.aikeyboard.core.translate.TranslatorPool
import com.manjul.aikeyboard.core.translate.mlkit.MlKitLanguageDetector
import com.manjul.aikeyboard.core.translate.mlkit.MlKitTranslationEngine
import com.manjul.aikeyboard.core.translate.mlkit.ModelPresence
import com.manjul.aikeyboard.core.translate.mlkit.RemoteModelPresence
import com.manjul.aikeyboard.core.translate.mlkit.TranslatorHandle
import com.manjul.aikeyboard.core.translate.mlkit.mlKitTranslatorOpener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The app-wide translation pieces, owned by FlorisApplication (S2-mlkit). Building this does not touch ML Kit:
 * ML Kit is initialised by the first translate, detect or model-presence call (TRD §2.3), and at most one
 * `Translator` is loaded, closed after 60 s idle or on memory trim (TRD §6.4).
 */
class TranslationCore(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val pool = TranslatorPool<LangPair, TranslatorHandle>(scope = scope, open = mlKitTranslatorOpener(context))

    val presence: ModelPresence = RemoteModelPresence(context)

    val detector = MlKitLanguageDetector(context)

    /** In-memory LRU; cleared when a field turns sensitive (Task 7/8). */
    val cache = TranslationCache()

    val router = TranslationEngineRouter(
        onDevice = MlKitTranslationEngine(presence, pool, Dispatchers.IO),
        cloud = CloudTranslationEngine(),
        resolver = SourceResolver(detector),
        cache = cache,
    )
}
