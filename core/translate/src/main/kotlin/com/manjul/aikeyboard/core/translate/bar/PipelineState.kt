/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate.bar

import com.manjul.aikeyboard.core.translate.FailKind
import com.manjul.aikeyboard.core.translate.LangPair

/**
 * What the bar shows. [input] and [output] are user content, never logged; [toString] omits them.
 *
 * @param output the last good translation; kept when a later request fails, `null` for blank input.
 * @param seq the number of the latest request; results of older requests are dropped.
 */
data class PipelineState(
    val input: String,
    val output: String?,
    val seq: Long,
    val inFlight: Boolean,
    val status: BarStatus,
) {
    override fun toString(): String =
        "PipelineState(inputChars=${input.length}, outputChars=${output?.length}, seq=$seq, inFlight=$inFlight, status=$status)"

    companion object {
        val Initial = PipelineState(input = "", output = null, seq = 0, inFlight = false, status = BarStatus.Idle)
    }
}

/** The inline status chip under the bar. No text. */
sealed interface BarStatus {
    data object Idle : BarStatus

    data object Ok : BarStatus

    /** The pair's packs are missing; [bytes] is the download estimate for the offer. */
    data class NeedsDownload(val pair: LangPair, val bytes: Long) : BarStatus

    /** Only cloud can translate the pair, and cloud needs Pro. */
    data object CloudOnlyPro : BarStatus

    data object Offline : BarStatus

    data class Error(val kind: FailKind) : BarStatus
}
