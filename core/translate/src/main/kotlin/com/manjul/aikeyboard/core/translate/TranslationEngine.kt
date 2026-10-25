/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate

/**
 * A translation backend (ML Kit on device, cloud later). Request text is user content: implementations never
 * log it and never put it in an exception message.
 */
interface TranslationEngine {
    val id: EngineId

    /** Whether this engine can translate [pair] now, after a download, or not at all. Never downloads. */
    suspend fun supports(pair: LangPair): Support

    /**
     * Translates [req]. Cancellable; never throws except [kotlinx.coroutines.CancellationException], errors are
     * returned as [TranslateResult.Failed]. [TranslateRequest.source] is always set when the router calls it.
     */
    suspend fun translate(req: TranslateRequest): TranslateResult
}

