/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.stickers

import androidx.annotation.StringRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * A short message shown inside the sticker panel. It replaces a Toast: the system suppresses toasts of an app
 * without the POST_NOTIFICATIONS permission, so the user saw no feedback (DT-Q.3). [message] holds the string
 * resource while the notice is visible and goes back to null after [durationMs]. Main thread only.
 */
class StickerNotice(
    private val scope: CoroutineScope,
    private val durationMs: Long = DURATION_MS,
) {
    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message

    private var expiry: Job? = null

    fun show(@StringRes res: Int) {
        expiry?.cancel()
        _message.value = res
        expiry = scope.launch {
            delay(durationMs)
            _message.value = null
        }
    }

    fun clear() {
        expiry?.cancel()
        _message.value = null
    }

    companion object {
        const val DURATION_MS = 3_000L
    }
}
