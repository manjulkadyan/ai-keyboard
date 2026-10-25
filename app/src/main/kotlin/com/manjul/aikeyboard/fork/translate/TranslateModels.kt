/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translate

import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.translate.LangPair
import com.manjul.aikeyboard.core.translate.mlkit.TranslateModelStore

/**
 * The translation models the bar needs: which languages are on the device (a snapshot, for the "Download" chip
 * size) and the user-started download. [StoreTranslateModels] serves it from the app's `TranslateModelStore`.
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
 * [TranslateModels] over the app's [TranslateModelStore] (S2-packs): a download is the store's app-owned WorkManager
 * job, so it keeps going after the keyboard hides, Wi-Fi only unless the user turned that off.
 */
class StoreTranslateModels(private val store: () -> TranslateModelStore) : TranslateModels {
    override fun downloaded(): Set<Lang> = store().ready()

    override suspend fun refresh() = store().refresh()

    override fun download(pair: LangPair) = store().download(pair)
}
