/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate.mlkit

import com.manjul.aikeyboard.core.translate.EngineId
import com.manjul.aikeyboard.core.translate.FailKind
import com.manjul.aikeyboard.core.translate.LangPair
import com.manjul.aikeyboard.core.translate.ModelSizes
import com.manjul.aikeyboard.core.translate.Support
import com.manjul.aikeyboard.core.translate.TranslateRequest
import com.manjul.aikeyboard.core.translate.TranslateResult
import com.manjul.aikeyboard.core.translate.TranslationEngine
import com.manjul.aikeyboard.core.translate.TranslatorPool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * ML Kit on-device translation (TRD §4.3). Request text is user content: never logged, and ML Kit exception
 * messages are dropped by [MlKitErrors].
 *
 * Never downloads: [translate] re-checks model presence first, because ML Kit's `Translator.translate` would
 * otherwise start a download on its own. Models are loaded through the [pool] (one at a time, closed when
 * idle). Each call has a [timeoutMs] limit.
 */
class MlKitTranslationEngine(
    private val presence: ModelPresence,
    private val pool: TranslatorPool<LangPair, TranslatorHandle>,
    private val io: CoroutineDispatcher,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) : TranslationEngine {
    override val id: EngineId = EngineId.MLKIT

    override suspend fun supports(pair: LangPair): Support {
        if (MlKitLanguages.toMlKit(pair.source) == null || MlKitLanguages.toMlKit(pair.target) == null) return Support.NONE
        val downloaded = try {
            presence.downloaded()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptySet()
        }
        return if (ModelSizes.missingFor(pair, downloaded).isEmpty()) Support.ON_DEVICE else Support.NEEDS_DOWNLOAD
    }

    override suspend fun translate(req: TranslateRequest): TranslateResult {
        val source = req.source ?: return TranslateResult.Failed(FailKind.ENGINE)
        val pair = LangPair(source, req.target)
        when (supports(pair)) {
            Support.ON_DEVICE -> Unit
            Support.NEEDS_DOWNLOAD -> return TranslateResult.Failed(FailKind.NO_PACK)
            Support.CLOUD_ONLY, Support.NONE -> return TranslateResult.Failed(FailKind.UNSUPPORTED)
        }
        return try {
            val text = withTimeout(timeoutMs) {
                withContext(io) { pool.use(pair) { it.translate(req.text) } }
            }
            TranslateResult.Ok(text, source, id)
        } catch (_: TimeoutCancellationException) {
            TranslateResult.Failed(FailKind.TIMEOUT)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TranslateResult.Failed(MlKitErrors.kind(e))
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 5_000L
    }
}
