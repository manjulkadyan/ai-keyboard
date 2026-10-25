/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate.mlkit

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.manjul.aikeyboard.core.translate.Lang
import kotlinx.coroutines.tasks.await

/**
 * The translation models on the device, as ML Kit keeps them (plan decision 4: no Room mirror). Language tags
 * only. Every call may throw; callers map failures to closed values and drop the message.
 */
interface RemoteModels {
    suspend fun downloaded(): Set<Lang>

    /** Suspends until the model is on the device. ML Kit has no byte progress and no cancel. */
    suspend fun download(lang: Lang, wifiOnly: Boolean)

    suspend fun delete(lang: Lang)
}

/** [RemoteModels] over ML Kit's `RemoteModelManager`. Initialises ML Kit on first call, never at process start. */
class MlKitRemoteModels(private val context: Context) : RemoteModels {
    private val presence = RemoteModelPresence(context)

    override suspend fun downloaded(): Set<Lang> = presence.downloaded()

    override suspend fun download(lang: Lang, wifiOnly: Boolean) {
        val conditions = DownloadConditions.Builder().apply { if (wifiOnly) requireWifi() }.build()
        manager().download(model(lang), conditions).await()
    }

    override suspend fun delete(lang: Lang) {
        manager().deleteDownloadedModel(model(lang)).await()
    }

    private fun manager(): RemoteModelManager {
        MlKitBootstrap.ensure(context)
        return RemoteModelManager.getInstance()
    }

    private fun model(lang: Lang): TranslateRemoteModel {
        val code = requireNotNull(MlKitLanguages.toMlKit(lang)) { "unsupported language" }
        return TranslateRemoteModel.Builder(code).build()
    }
}
