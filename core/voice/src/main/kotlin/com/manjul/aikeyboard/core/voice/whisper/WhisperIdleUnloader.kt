/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.voice.whisper

import android.content.ComponentCallbacks2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Frees the loaded Whisper model [IDLE_MS] after the last [touch], or at once when the system reports
 * memory pressure ([onTrimMemory], see there for the levels). [free] must not block:
 * it runs on the main thread from onTrimMemory. The model is loaded again on next use.
 */
class WhisperIdleUnloader(
    private val clock: () -> Long,
    private val scope: CoroutineScope,
    private val free: () -> Unit,
) {
    private val lock = Any()
    private var lastUse = 0L
    private var job: Job? = null

    fun touch() {
        synchronized(lock) {
            lastUse = clock()
            if (job?.isActive != true) job = scope.launch { freeWhenIdle() }
        }
    }

    /**
     * Frees on RUNNING_LOW, RUNNING_CRITICAL and BACKGROUND or above (R38). UI_HIDDEN only means our settings UI
     * went to the background; freeing then would force a reload on the next dictation.
     */
    @Suppress("DEPRECATION") // The RUNNING_* levels are still delivered below API 34.
    fun onTrimMemory(level: Int) {
        val free = level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
        if (!free) return
        synchronized(lock) {
            job?.cancel()
            job = null
        }
        free()
    }

    private suspend fun freeWhenIdle() {
        while (true) {
            val wait = synchronized(lock) { lastUse + IDLE_MS - clock() }
            if (wait <= 0) break
            delay(wait)
        }
        synchronized(lock) { job = null }
        free()
    }

    companion object {
        const val IDLE_MS = 60_000L
    }
}
