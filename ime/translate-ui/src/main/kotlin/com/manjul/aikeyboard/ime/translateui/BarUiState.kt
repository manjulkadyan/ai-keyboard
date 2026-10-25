/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.translateui

import com.manjul.aikeyboard.core.datastore.BarMode
import com.manjul.aikeyboard.core.translate.LangPair
import com.manjul.aikeyboard.core.translate.bar.BarStatus

/**
 * What the keyboard shows for the Translate bar. [Open.input] and [Open.preview] are user content: never logged
 * (`toString` reports lengths only).
 */
sealed interface BarUiState {
    data object Hidden : BarUiState

    /**
     * The bar is open. [preview] is the last good translation of [input] (kept through failures, null until the
     * first one); [status] drives the status chip; [undoAvailable] shows the undo chip of our last insert.
     */
    data class Open(
        val input: String,
        val preview: String?,
        val pair: LangPair,
        val mode: BarMode,
        val status: BarStatus,
        val undoAvailable: Boolean,
        val inFlight: Boolean,
    ) : BarUiState {
        override fun toString(): String =
            "Open(inputChars=${input.length}, previewChars=${preview?.length}, pair=$pair, mode=$mode, " +
                "status=$status, undoAvailable=$undoAvailable, inFlight=$inFlight)"
    }
}

/** Why the bar closed. */
enum class CloseReason {
    /** The user closed it (close button, back gesture, chip). */
    USER,

    /** The editor moved to another field. */
    FIELD_CHANGE,

    /** The field became sensitive while the bar was open: buffer, preview and this field's cache entries go. */
    SENSITIVE,

    /** The keyboard was hidden; pair and mode are kept. */
    HIDDEN,
}
