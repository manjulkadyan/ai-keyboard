/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translator

import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.translate.LangPair
import com.manjul.aikeyboard.core.translate.bar.BarStatus

/** Whether the target language can be spoken (PRD FR-12 AC4). */
enum class SpeakAvailability {
    /** Not checked yet (no translation shown, or the TTS engine is still binding). */
    UNKNOWN,
    AVAILABLE,

    /** The language is supported but its voice is not installed: the screen offers "Install voice". */
    MISSING_DATA,
    NOT_SUPPORTED,
}

/** The Translator screen's mic. No text. */
sealed interface MicState {
    data object Idle : MicState

    data object Listening : MicState

    /** No platform recognizer can serve the source language now (decision 7: no Whisper on this screen). */
    data object Unavailable : MicState

    data object NoPermission : MicState

    /** The recognizer failed (network, busy, ...); the input keeps what was heard. */
    data object Error : MicState
}

/**
 * What the Translator screen shows. [input] and [output] are user content: [toString] leaves them out (PC-10).
 *
 * @param pair the shared pair (plan decision 2); with [autoDetect] its source is only the fallback.
 * @param detected the source language detection picked for [output], shown as "Detected: Urdu" in auto mode.
 * @param output the last good translation; kept when a later request fails.
 * @param favorite the current edit session's history row is a favourite.
 */
data class TranslatorUiState(
    val input: String = "",
    val output: String? = null,
    val pair: LangPair = LangPair(Lang.English, Lang("es")),
    val autoDetect: Boolean = false,
    val detected: Lang? = null,
    val status: BarStatus = BarStatus.Idle,
    val inFlight: Boolean = false,
    val historyEnabled: Boolean = true,
    val favorite: Boolean = false,
    val speak: SpeakAvailability = SpeakAvailability.UNKNOWN,
    val speaking: Boolean = false,
    /** Set by a speak tap while the voice is missing; the screen shows the "Install voice" offer. */
    val ttsInstallOffered: Boolean = false,
    val mic: MicState = MicState.Idle,
) {
    val count: Int get() = input.length

    val atLimit: Boolean get() = input.length >= MAX_CHARS

    override fun toString(): String =
        "TranslatorUiState(inputChars=${input.length}, outputChars=${output?.length}, pair=$pair, " +
            "autoDetect=$autoDetect, detected=$detected, status=$status, inFlight=$inFlight, " +
            "historyEnabled=$historyEnabled, favorite=$favorite, speak=$speak, speaking=$speaking, " +
            "ttsInstallOffered=$ttsInstallOffered, mic=$mic)"

    companion object {
        /** PRD FR-12: the input holds at most 5,000 characters. */
        const val MAX_CHARS = 5_000
    }
}
