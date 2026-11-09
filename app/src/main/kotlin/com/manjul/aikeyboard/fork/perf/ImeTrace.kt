/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.perf

import android.os.Build
import android.os.Trace
import android.view.View
import android.view.ViewTreeObserver

/**
 * Trace sections for the TRD §6.4 keyboard budgets (S4 Task 13). `ImeShowBenchmark` reads them with
 * `TraceSectionMetric`, and a Perfetto trace shows them by name.
 *
 * - [IME_SHOW]: an async section from `onStartInputView` to the next draw of the keyboard view. API 29+ only,
 *   because async sections need `Trace.beginAsyncSection`. On older devices nothing is traced.
 * - [KEY_COMMIT]: a plain section around the key-up handler, which commits the character to the editor.
 *
 * The section names hold no user data. Everything runs on the main thread, so the state needs no lock.
 */
object ImeTrace {
    const val IME_SHOW = "aikb_ime_show"
    const val KEY_COMMIT = "aikb_key_commit"

    private const val IME_SHOW_COOKIE = 0x41_4b_42 // any fixed value; one show is open at a time
    private var showOpen = false
    private var drawListener: ViewTreeObserver.OnDrawListener? = null

    /** Opens [IME_SHOW] and closes it at the next draw of [view]. A show that is still open is closed first. */
    fun beginImeShow(view: View?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        endImeShow(view)
        if (view == null) return
        Trace.beginAsyncSection(IME_SHOW, IME_SHOW_COOKIE)
        showOpen = true
        val listener = object : ViewTreeObserver.OnDrawListener {
            override fun onDraw() {
                endImeShow(null)
                // A draw listener cannot remove itself during onDraw, so remove it after this frame.
                view.post { removeListener(view, this) }
            }
        }
        drawListener = listener
        view.viewTreeObserver.addOnDrawListener(listener)
    }

    /** Closes an open [IME_SHOW], for example when the keyboard hides before its first draw. */
    fun endImeShow(view: View?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (showOpen) {
            Trace.endAsyncSection(IME_SHOW, IME_SHOW_COOKIE)
            showOpen = false
        }
        val listener = drawListener
        if (view != null && listener != null) removeListener(view, listener)
    }

    private fun removeListener(view: View, listener: ViewTreeObserver.OnDrawListener) {
        if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnDrawListener(listener)
        if (drawListener === listener) drawListener = null
    }

    /** Runs [block] inside the [KEY_COMMIT] section. */
    inline fun <T> keyCommit(block: () -> T): T {
        Trace.beginSection(KEY_COMMIT)
        try {
            return block()
        } finally {
            Trace.endSection()
        }
    }
}
