/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.aiui

import com.manjul.aikeyboard.core.ai.AiError
import com.manjul.aikeyboard.core.ai.RewriteMode
import com.manjul.aikeyboard.core.analytics.PolishMode

/**
 * What the AI rewrite sheet shows ([AiActionController.state], and [PolishController.state] for Polish). Preview text
 * is user content: no [toString] prints it.
 *
 * Polish reuses these states: its [Streaming], [Ready], [Replaced] and [Reporting] carry the [PolishMode] in `polish`
 * (and [RewriteMode.FIX] in `mode`, which the sheet does not show for Polish). A rewrite leaves `polish` null.
 */
sealed interface AiSheetState {
    /** The sheet is closed. */
    data object Hidden : AiSheetState

    /** Tone chips; [RewriteMode.TRANSLATE] is in [tones] only when there is a [translateTarget] (a language tag). */
    data class ChooseTone(val tones: List<RewriteMode>, val translateTarget: String?) : AiSheetState

    /** The output so far; [stillWorking] once no text came for `ai_still_working_ms`. Stop is offered. */
    data class Streaming(
        val mode: RewriteMode,
        val text: String,
        val stillWorking: Boolean,
        val polish: PolishMode? = null,
    ) : AiSheetState {
        override fun toString(): String =
            "Streaming(mode=$mode, polish=$polish, chars=${text.length}, stillWorking=$stillWorking)"
    }

    /** The finished (or stopped) output: Replace, Insert, Copy, Retry, Report. [trimmed]: it hit the token cap. */
    data class Ready(
        val mode: RewriteMode,
        val text: String,
        val trimmed: Boolean,
        val polish: PolishMode? = null,
    ) : AiSheetState {
        override fun toString(): String = "Ready(mode=$mode, polish=$polish, chars=${text.length}, trimmed=$trimmed)"
    }

    /**
     * The request failed. [AiError.Quota] shows the quota card. [canRetry] offers Retry (offline, timeout, rate
     * limit, unavailable).
     */
    data class Error(val error: AiError, val canRetry: Boolean) : AiSheetState

    /** Replace was refused because the captured text changed: nothing was written; Insert and Copy are offered. */
    data class TextChanged(val text: String) : AiSheetState {
        override fun toString(): String = "TextChanged(chars=${text.length})"
    }

    /** No AI consent yet: the app's consent screen was asked for; nothing was captured or sent. */
    data object NeedsConsent : AiSheetState

    /** There was no text to rewrite (empty field, or the field cannot be read). Nothing was sent. */
    data object NothingToRewrite : AiSheetState

    /** Replace wrote the output; Undo is offered for [AiActionController.UNDO_WINDOW_MS], then the sheet closes. */
    data class Replaced(val mode: RewriteMode, val polish: PolishMode? = null) : AiSheetState

    /** The report sheet over the output of [mode]; submit or cancel goes back to the output. */
    data class Reporting(val mode: RewriteMode, val polish: PolishMode? = null) : AiSheetState
}

/** Why the user reported AI output (`/v1/ai/report` `reason`). */
enum class ReportReason(val wire: String) {
    OFFENSIVE("offensive"),
    WRONG("wrong"),
    UNSAFE("unsafe"),
    OTHER("other"),
}

/**
 * One AI output report (PRD FR-15 AC9). [text] is the output and is non-null only when the user ticked "Include the
 * text"; [toString] never prints it.
 */
data class ReportPayload(val clientReqId: String, val promptId: String, val reason: ReportReason, val text: String?) {
    override fun toString(): String =
        "ReportPayload(id=$clientReqId, prompt=$promptId, reason=${reason.wire}, text=${text?.let { "${it.length} chars" }})"
}
