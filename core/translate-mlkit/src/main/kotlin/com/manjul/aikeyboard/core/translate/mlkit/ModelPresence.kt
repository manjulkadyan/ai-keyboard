/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate.mlkit

import android.content.Context
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.manjul.aikeyboard.core.translate.Lang
import kotlinx.coroutines.tasks.await

/** Which translation models are on the device. ML Kit is the source of truth (plan decision 4). */
fun interface ModelPresence {
    suspend fun downloaded(): Set<Lang>
}

/**
 * [ModelPresence] over ML Kit's `RemoteModelManager`. Initialises ML Kit on first call. The offline-packs
 * store (Task 5) wraps this with a cached `StateFlow`.
 */
class RemoteModelPresence(private val context: Context) : ModelPresence {
    override suspend fun downloaded(): Set<Lang> {
        MlKitBootstrap.ensure(context)
        return RemoteModelManager.getInstance()
            .getDownloadedModels(TranslateRemoteModel::class.java)
            .await()
            .map { Lang.of(it.language) }
            .toSet()
    }
}
