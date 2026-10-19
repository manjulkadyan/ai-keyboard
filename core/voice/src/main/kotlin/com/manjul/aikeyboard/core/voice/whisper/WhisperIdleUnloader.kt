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
 * memory pressure ([onTrimMemory] with a level of at least TRIM_MEMORY_RUNNING_LOW). [free] must not block:
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

    @Suppress("DEPRECATION") // TRIM_MEMORY_RUNNING_LOW is still delivered below API 34.
    fun onTrimMemory(level: Int) {
        if (level < ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) return
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
