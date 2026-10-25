/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translate

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.translate.LangPair
import com.manjul.aikeyboard.core.translate.ModelSizes
import com.manjul.aikeyboard.core.translate.mlkit.MlKitBootstrap
import com.manjul.aikeyboard.core.translate.mlkit.MlKitLanguages
import com.manjul.aikeyboard.core.translate.mlkit.ModelPresence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The translation models the bar needs: which languages are on the device (a snapshot, for the "Download" chip
 * size) and the user-started download. Task 5's `TranslateModelStore` (WorkManager job, Offline languages screen)
 * replaces [MlKitTranslateModels] behind this interface.
 */
interface TranslateModels {
    /** The last known downloaded languages; empty until [refresh] has run. */
    fun downloaded(): Set<Lang>

    /** Re-reads the downloaded languages. Initialises ML Kit, so only call it after the user opened the bar. */
    suspend fun refresh()

    /** Downloads what [pair] is missing. Only ever called from the user's tap on the "Download" chip. */
    fun download(pair: LangPair)
}

/**
 * Interim [TranslateModels] until Task 5: downloads through ML Kit's `RemoteModelManager` in the app's
 * [scope], so a download keeps going after the keyboard hides. Wi-Fi only unless the user turned that off.
 */
class MlKitTranslateModels(
    private val context: Context,
    private val presence: ModelPresence,
    private val scope: CoroutineScope,
    private val wifiOnly: () -> Boolean,
) : TranslateModels {
    @Volatile private var known: Set<Lang> = emptySet()

    override fun downloaded(): Set<Lang> = known

    override suspend fun refresh() {
        known = runCatching { presence.downloaded() }.getOrDefault(known)
    }

    override fun download(pair: LangPair) {
        val missing = ModelSizes.missingFor(pair, known)
        if (missing.isEmpty()) return
        MlKitBootstrap.ensure(context)
        val conditions = DownloadConditions.Builder().apply { if (wifiOnly()) requireWifi() }.build()
        for (lang in missing) {
            val code = MlKitLanguages.toMlKit(lang) ?: continue
            RemoteModelManager.getInstance()
                .download(TranslateRemoteModel.Builder(code).build(), conditions)
                .addOnCompleteListener { scope.launch { refresh() } }
        }
    }
}
