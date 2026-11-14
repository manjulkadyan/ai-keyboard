/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translit

import android.os.SystemClock
import com.manjul.aikeyboard.FlorisImeService
import com.manjul.aikeyboard.fork.voice.ImeTextTarget
import com.manjul.aikeyboard.ime.editing.TextTarget
import com.manjul.aikeyboard.ime.nlp.SuggestionCandidate

/**
 * Backspace right after a space commit in the host field puts the Roman word back, once (plan decision 4). The
 * upstream revert record (`phantomSpace.candidateForRevert`) is cleared by the space that follows the commit, so it
 * never sees a space commit. This class keeps its own record instead.
 *
 * It acts only while the text right before the cursor still equals the native word plus the space, on the same input
 * connection, within [windowMs], and with no other key in between. Then it removes "native + space" and commits the
 * Roman word. In every other case it touches nothing, and the caller runs the normal backspace.
 *
 * Main thread only. The words are user content: nothing here logs them, and [toString] holds no text.
 */
class TranslitRevert(
    private val target: TextTarget,
    private val connection: () -> Any?,
    private val clock: () -> Long,
    private val windowMs: Long = 5_000,
) {
    private class Pending(val candidate: TranslitCandidate, val written: String, val connection: Any, val at: Long)

    private var pending: Pending? = null

    /** Whether a later [undo] may act (something is recorded). */
    val available: Boolean get() = pending != null

    /**
     * The space key finished. [candidate] is what the space committed (null for none) and [spaceCommitted] tells
     * whether a space went in after it. Only a native [TranslitCandidate] followed by a space is recorded.
     */
    fun onSpace(candidate: SuggestionCandidate?, spaceCommitted: Boolean) {
        pending = null
        if (candidate !is TranslitCandidate || candidate.latin || !spaceCommitted) return
        val ic = connection() ?: return
        pending = Pending(candidate, candidate.text.toString() + " ", ic, clock())
    }

    /** Any other key, or a new field: a later [undo] does nothing. */
    fun invalidate() {
        pending = null
    }

    /**
     * Replaces "native + space" before the cursor with the Roman word and returns the reverted candidate, or returns
     * null and changes nothing. Works at most once per record.
     */
    fun undo(): TranslitCandidate? {
        val p = pending ?: return null
        pending = null
        if (p.connection !== connection()) return null
        if (clock() - p.at !in 0..windowMs) return null
        if (!target.deleteBeforeCursorIfEquals(p.written)) return null
        target.commit(p.candidate.typed)
        return p.candidate
    }

    override fun toString(): String = "TranslitRevert(available=$available)"

    companion object {
        /** The host field of the running IME: the current input connection, uptime clock. */
        fun forIme(isRawInputEditor: () -> Boolean): TranslitRevert = TranslitRevert(
            target = ImeTextTarget(FlorisImeService::currentInputConnection, isRawInputEditor, setOwnsComposing = {}),
            connection = FlorisImeService::currentInputConnection,
            clock = SystemClock::uptimeMillis,
        )
    }
}
