/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ai

/**
 * One AI call to kb-api (TRD §5.3). The text is user content: [toString] prints only its length.
 * [clientReqId] makes a retry count once against the quota, so a retry must reuse it.
 */
sealed interface AiRequest {
    val clientReqId: String

    /** The user text that is sent, checked against [AiClient.MAX_INPUT_CHARS]. */
    val input: String

    /** `POST /v1/rewrite`, one streamed mode. [targetLang] is required for [RewriteMode.TRANSLATE] only. */
    data class Rewrite(
        val mode: RewriteMode,
        val locale: String,
        val text: String,
        val targetLang: String?,
        override val clientReqId: String,
    ) : AiRequest {
        override val input: String get() = text

        override fun toString(): String =
            "Rewrite(mode=${mode.wire}, locale=$locale, target=$targetLang, chars=${text.length}, id=$clientReqId)"
    }

    /** `POST /v1/polish`: clean up a dictated [transcript] and translate it to [targetLang]. */
    data class Polish(
        val transcript: String,
        val sourceLang: String?,
        val targetLang: String,
        val script: OutputScript,
        override val clientReqId: String,
    ) : AiRequest {
        override val input: String get() = transcript

        override fun toString(): String =
            "Polish(source=$sourceLang, target=$targetLang, script=${script.wire}, chars=${transcript.length}, id=$clientReqId)"
    }
}

/** Rewrite modes, by their kb-api wire name (`RewriteService.MODES`). */
enum class RewriteMode(val wire: String) {
    FIX("fix"),
    FORMAL("formal"),
    FRIENDLY("friendly"),
    SHORTER("shorter"),
    LONGER("longer"),
    EMOJIFY("emojify"),
    TRANSLATE("translate"),
}

/**
 * Polish output script (PRD FR-16 AC4, Sprint 3 decision 12). The wire names are the kb-api enum
 * (`openapi.yaml` `PolishRequest.script`: `MATCH_INPUT`, `ROMAN`, `NATIVE`).
 */
enum class OutputScript(val wire: String) {
    MATCH_INPUT("MATCH_INPUT"),
    ROMAN("ROMAN"),
    NATIVE("NATIVE"),
}
