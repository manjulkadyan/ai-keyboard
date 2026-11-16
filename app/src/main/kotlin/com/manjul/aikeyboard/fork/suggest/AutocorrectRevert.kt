/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.suggest

import android.os.SystemClock
import com.manjul.aikeyboard.FlorisImeService
import com.manjul.aikeyboard.fork.voice.ImeTextTarget
import com.manjul.aikeyboard.ime.editing.TextTarget
import com.manjul.aikeyboard.ime.nlp.SuggestionCandidate

/**
 * Backspace right after an autocorrect on space puts the typed word back, once (DT-G.2). The upstream revert record
 * (`phantomSpace.candidateForRevert`) is cleared by the space that follows the commit, so the first backspace used to
 * delete only that space. This class keeps its own record, like `TranslitRevert` does for Roman text.
 *
 * It acts only while the text right before the cursor still equals the corrected word plus the space, on the same
 * input connection, within [windowMs], and with no other key in between. Then it removes "corrected + space" and
 * commits the typed word. Otherwise it touches nothing and the caller runs the normal backspace.
 *
 * Main thread only. The words are user content: nothing here logs them, and [toString] holds no text.
 */
class AutocorrectRevert(
    private val target: TextTarget,
    private val connection: () -> Any?,
    private val clock: () -> Long,
    private val windowMs: Long = 5_000,
) {
    private class Pending(val candidate: LatinWordCandidate, val written: String, val connection: Any, val at: Long)

    private var pending: Pending? = null

    /** Whether a later [undo] may act (something is recorded). */
    val available: Boolean get() = pending != null

    /** The space key finished. Only a [LatinWordCandidate] that changed the word, followed by a space, is recorded. */
    fun onSpace(candidate: SuggestionCandidate?, spaceCommitted: Boolean) {
        pending = null
        if (candidate !is LatinWordCandidate || !spaceCommitted) return
        val corrected = candidate.text.toString()
        if (corrected == candidate.typed) return
        val ic = connection() ?: return
        pending = Pending(candidate, "$corrected ", ic, clock())
    }

    /** Any other key, or a new field: a later [undo] does nothing. */
    fun invalidate() {
        pending = null
    }

    /** Replaces "corrected + space" with the typed word and returns the candidate, or null and changes nothing. */
    fun undo(): LatinWordCandidate? {
        val p = pending ?: return null
        pending = null
        if (p.connection !== connection()) return null
        if (clock() - p.at !in 0..windowMs) return null
        if (!target.deleteBeforeCursorIfEquals(p.written)) return null
        target.commit(p.candidate.typed)
        return p.candidate
    }

    override fun toString(): String = "AutocorrectRevert(available=$available)"

    companion object {
        /** The host field of the running IME: the current input connection, uptime clock. */
        fun forIme(isRawInputEditor: () -> Boolean): AutocorrectRevert = AutocorrectRevert(
            target = ImeTextTarget(FlorisImeService::currentInputConnection, isRawInputEditor, setOwnsComposing = {}),
            connection = FlorisImeService::currentInputConnection,
            clock = SystemClock::uptimeMillis,
        )
    }
}
