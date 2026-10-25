/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate

/**
 * Disabled cloud engine (plan decision 1, OD-05 open). Reports [Support.CLOUD_ONLY] for the languages no
 * on-device engine covers, and fails every request with [FailKind.CLOUD_UNAVAILABLE]; it makes no network
 * call. Request text is user content, never logged.
 *
 * TODO(OD-05): pick the vendor. The real engine sends the text in a POST body only (never a URL or header),
 * uses OkHttp with 5 s connect / 10 s read timeouts and is cancelled with the calling coroutine (TRD §4.3.2).
 */
class CloudTranslationEngine : TranslationEngine {
    override val id: EngineId = EngineId.CLOUD

    override suspend fun supports(pair: LangPair): Support =
        if (pair.source in CLOUD_ONLY_LANGS || pair.target in CLOUD_ONLY_LANGS) Support.CLOUD_ONLY else Support.NONE

    override suspend fun translate(req: TranslateRequest): TranslateResult =
        TranslateResult.Failed(FailKind.CLOUD_UNAVAILABLE)

    companion object {
        /** Languages ML Kit does not translate; only a cloud engine could. */
        val CLOUD_ONLY_LANGS: Set<Lang> = setOf(Lang("ps"))
    }
}
