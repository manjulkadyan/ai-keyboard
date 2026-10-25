/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.privacy

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * "Make this app private" from the smartbar adds the app at once, then offers Undo for [windowMs] through the
 * in-keyboard "App made private" message ([visible]). Undo removes the app again; the policy controller re-evaluates
 * the field on the settings change, so the keyboard returns to the normal state live. A field change or the
 * window ending dismisses the message and the app stays private.
 *
 * Main thread only. The package is held only until the message goes away and is never logged; no analytics event
 * is sent for Undo (PRD §12 has none).
 */
class PrivateAppUndo(
    private val add: suspend (String) -> Unit,
    private val remove: suspend (String) -> Unit,
    private val scope: CoroutineScope,
    private val windowMs: Long = WINDOW_MS,
) {
    private val _visible = MutableStateFlow(false)

    /** True while "App made private" with Undo is shown. */
    val visible: StateFlow<Boolean> = _visible

    private var pendingPackage: String? = null
    private var expiry: Job? = null
    /** Changes on every field change, so an add that completes after it shows no message for the old field. */
    private var field = 0

    fun makePrivate(packageName: String?) {
        if (packageName.isNullOrBlank()) return
        val token = field
        scope.launch {
            add(packageName)
            if (token != field) return@launch
            show(packageName)
        }
    }

    /** Removes the app from the private apps again, if the message is still shown. */
    fun undo() {
        val pkg = pendingPackage ?: return
        hide()
        scope.launch { remove(pkg) }
    }

    /** A new field started or the field closed: the message goes away and the app stays private. */
    fun onFieldChange() {
        field++
        hide()
    }

    private fun show(packageName: String) {
        expiry?.cancel()
        pendingPackage = packageName
        _visible.value = true
        expiry = scope.launch {
            delay(windowMs)
            hide()
        }
    }

    private fun hide() {
        expiry?.cancel()
        expiry = null
        pendingPackage = null
        _visible.value = false
    }

    companion object {
        /** How long Undo is offered, the same window as the Translate bar's Undo. */
        const val WINDOW_MS = 5_000L
    }
}
