/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate.mlkit

import android.content.Context
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.manjul.aikeyboard.core.translate.LangPair
import kotlinx.coroutines.tasks.await

/**
 * One loaded translation model pair. A seam over ML Kit's [Translator] so the engine can be tested without a
 * model. Text is user content, never logged.
 */
interface TranslatorHandle : AutoCloseable {
    /** Translates [text]; cancellable (an ML Kit `Task` keeps running, its result is then ignored). */
    suspend fun translate(text: String): String
}

private class MlKitTranslatorHandle(private val translator: Translator) : TranslatorHandle {
    override suspend fun translate(text: String): String = translator.translate(text).await()

    override fun close() {
        translator.close()
    }
}

/**
 * Opens ML Kit translators for a [com.manjul.aikeyboard.core.translate.TranslatorPool]. Initialises ML Kit on
 * first use. Opening never downloads: the engine checks model presence first, and `downloadModelIfNeeded` is
 * never called here.
 */
fun mlKitTranslatorOpener(context: Context): suspend (LangPair) -> TranslatorHandle = { pair ->
    MlKitBootstrap.ensure(context)
    val source = requireNotNull(MlKitLanguages.toMlKit(pair.source)) { "unsupported source ${pair.source}" }
    val target = requireNotNull(MlKitLanguages.toMlKit(pair.target)) { "unsupported target ${pair.target}" }
    val options = TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build()
    MlKitTranslatorHandle(Translation.getClient(options))
}
