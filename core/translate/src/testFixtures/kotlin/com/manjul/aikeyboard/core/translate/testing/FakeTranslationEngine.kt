/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate.testing

import com.manjul.aikeyboard.core.translate.EngineId
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.translate.LangPair
import com.manjul.aikeyboard.core.translate.Support
import com.manjul.aikeyboard.core.translate.TranslateRequest
import com.manjul.aikeyboard.core.translate.TranslateResult
import com.manjul.aikeyboard.core.translate.TranslationEngine
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Scriptable [TranslationEngine] for tests. Records every request, the number of requests in flight and the
 * highest that number reached.
 *
 * The default result is `"<target>text"`, e.g. `"<es>hello"`.
 *
 * @param delays virtual time each request takes, by request text.
 * @param ignoreCancellation when true the delay runs in [NonCancellable], like an ML Kit `Task` that cannot be
 *   cancelled: the result still comes back after the caller moved on.
 */
class FakeTranslationEngine(
    override val id: EngineId = EngineId.MLKIT,
    private val delays: (String) -> Long = { 0L },
    private val results: (TranslateRequest) -> TranslateResult = { req ->
        TranslateResult.Ok("<${req.target.tag}>${req.text}", req.source ?: Lang("und"), id)
    },
    private val supports: (LangPair) -> Support = { Support.ON_DEVICE },
    private val ignoreCancellation: Boolean = false,
) : TranslationEngine {
    private val _calls = mutableListOf<TranslateRequest>()
    val calls: List<TranslateRequest> get() = synchronized(_calls) { _calls.toList() }

    private val _supportQueries = mutableListOf<LangPair>()
    val supportQueries: List<LangPair> get() = synchronized(_supportQueries) { _supportQueries.toList() }

    @Volatile var inFlight: Int = 0
        private set

    @Volatile var maxInFlight: Int = 0
        private set

    override suspend fun supports(pair: LangPair): Support {
        synchronized(_supportQueries) { _supportQueries += pair }
        return supports.invoke(pair)
    }

    override suspend fun translate(req: TranslateRequest): TranslateResult {
        synchronized(_calls) {
            _calls += req
            inFlight++
            if (inFlight > maxInFlight) maxInFlight = inFlight
        }
        try {
            val ms = delays(req.text)
            if (ms > 0) {
                if (ignoreCancellation) withContext(NonCancellable) { delay(ms) } else delay(ms)
            }
            return results(req)
        } finally {
            synchronized(_calls) { inFlight-- }
        }
    }
}
