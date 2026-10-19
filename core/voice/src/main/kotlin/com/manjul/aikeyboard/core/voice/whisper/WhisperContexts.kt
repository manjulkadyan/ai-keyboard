/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.voice.whisper

import com.manjul.aikeyboard.lib.whisper.WhisperApi
import com.manjul.aikeyboard.lib.whisper.WhisperException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The process-wide loaded Whisper model, shared by every [WhisperSpeechEngine]. Loads and transcriptions are
 * serialised (one context runs one transcription at a time) and must run off the main thread. [abort] and
 * [release] never block, so they are safe from the main thread.
 */
class WhisperContexts(
    private val api: WhisperApi,
    private val threads: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, MAX_THREADS),
) {
    private val mutex = Mutex()
    private val lock = Any()
    private var ctx = 0L
    private var loadedPath: String? = null
    private var owner: Any? = null
    private var releaseWhenDone = false
    private var aborts = 0

    /** Loads [modelPath] ahead of [transcribe]. Load failures are left for [transcribe] to report. */
    suspend fun preload(modelPath: String) = mutex.withLock {
        runCatching { acquire(modelPath, owner = null) }
        synchronized(lock) { finishUse() }
    }

    /**
     * Transcribes on the calling thread. [owner] identifies the caller for [abort].
     *
     * @throws WhisperException if the model cannot be loaded or whisper.cpp fails or was aborted.
     * @throws IllegalArgumentException if the context was freed under the call.
     * @throws IllegalStateException if the context is busy.
     */
    suspend fun transcribe(owner: Any, modelPath: String, pcm: FloatArray, lang: String): String = mutex.withLock {
        try {
            api.transcribe(acquire(modelPath, owner), pcm, lang)
        } finally {
            synchronized(lock) { finishUse() }
        }
    }

    /** Aborts [owner]'s running transcription by freeing the context; it then throws. No-op for other callers. */
    fun abort(owner: Any) = synchronized(lock) {
        if (this.owner === owner) {
            aborts++
            freeLocked()
        }
    }

    /** Frees the model now, or after the running transcription when one is in flight. */
    fun release() = synchronized(lock) {
        if (owner != null) releaseWhenDone = true else freeLocked()
    }

    private fun acquire(modelPath: String, owner: Any?): Long {
        val seen = synchronized(lock) {
            this.owner = owner
            if (ctx != 0L && loadedPath == modelPath) return ctx
            freeLocked()
            aborts
        }
        val handle = api.load(modelPath, threads)
        synchronized(lock) {
            if (handle == 0L) throw WhisperException("model could not be loaded")
            if (aborts != seen) {
                api.free(handle)
                throw WhisperException("aborted while loading")
            }
            ctx = handle
            loadedPath = modelPath
            return handle
        }
    }

    private fun finishUse() {
        owner = null
        if (releaseWhenDone) {
            releaseWhenDone = false
            freeLocked()
        }
    }

    private fun freeLocked() {
        val handle = ctx
        ctx = 0L
        loadedPath = null
        if (handle != 0L) api.free(handle)
    }

    private companion object {
        const val MAX_THREADS = 4
    }
}
