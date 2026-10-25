/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.processtext

import android.content.Intent
import com.manjul.aikeyboard.core.analytics.ProcessTextAction

/**
 * The text a `TranslateTextActivity` intent carries (PRD FR-14). The activity is exported, so [parse] trusts nothing:
 * only `PROCESS_TEXT` with a CharSequence `EXTRA_PROCESS_TEXT`, or `SEND` `text/plain` with a CharSequence
 * `EXTRA_TEXT`, yield an input; anything else (no text, blank text, another type, a Parcelable in place of the text,
 * a data URI, a parcel that fails to unpack) is `null` and the activity finishes. Never throws, never logs the text.
 */
data class ProcessTextInput(
    val text: String,
    val action: ProcessTextAction,
    /** Set by the caller for read-only selections; we never return a result for those. */
    val readOnly: Boolean,
    /** The text was cut to [MAX_CHARS]. */
    val truncated: Boolean,
) {
    /** Replace is offered only for an editable selection (FR-14 AC2). */
    val canReplace: Boolean get() = action == ProcessTextAction.PROCESS_TEXT && !readOnly

    override fun toString(): String =
        "ProcessTextInput(chars=${text.length}, action=$action, readOnly=$readOnly, truncated=$truncated)"

    companion object {
        const val MAX_CHARS = 5_000
        const val MIME_TEXT = "text/plain"

        fun parse(intent: Intent?): ProcessTextInput? = intent?.let { runCatching { read(it) }.getOrNull() }

        private fun read(intent: Intent): ProcessTextInput? {
            if (intent.data != null) return null
            return when (intent.action) {
                Intent.ACTION_PROCESS_TEXT -> {
                    val type = intent.type
                    if (type != null && type != MIME_TEXT) return null
                    val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT) ?: return null
                    val readOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
                    build(text, ProcessTextAction.PROCESS_TEXT, readOnly)
                }
                Intent.ACTION_SEND -> {
                    if (intent.type != MIME_TEXT) return null
                    val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT) ?: return null
                    build(text, ProcessTextAction.SEND, readOnly = true)
                }
                else -> null
            }
        }

        private fun build(text: CharSequence, action: ProcessTextAction, readOnly: Boolean): ProcessTextInput? {
            val plain = text.toString()
            if (plain.isBlank()) return null
            val cut = truncate(plain)
            return ProcessTextInput(cut, action, readOnly, truncated = cut.length < plain.length)
        }

        /** Cuts to [MAX_CHARS] without splitting a surrogate pair. */
        fun truncate(text: String): String {
            if (text.length <= MAX_CHARS) return text
            var end = MAX_CHARS
            if (Character.isHighSurrogate(text[end - 1])) end--
            return text.substring(0, end)
        }
    }
}
