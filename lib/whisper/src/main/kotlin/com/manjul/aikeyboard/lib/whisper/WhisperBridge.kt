/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.lib.whisper

/** The raw native calls, a seam so [WhisperBridge]'s guards are testable on the JVM. */
internal interface WhisperNativeCalls {
    fun isAvailable(): Boolean

    /** Returns the native session pointer, or 0 on failure. */
    fun init(modelPath: String, threads: Int): Long

    /**
     * Returns the concatenated segment text as raw UTF-8 bytes (possibly malformed), or null if whisper_full
     * failed or was aborted.
     */
    fun transcribe(ptr: Long, pcm: FloatArray, lang: String, translate: Boolean): ByteArray?

    /** Asks a running [transcribe] on [ptr] to stop. Never blocks; safe while the decode runs. */
    fun abort(ptr: Long)

    fun free(ptr: Long)
}

/** Upper bound for whisper threads: more only oversubscribes big.LITTLE cores in the IME process. */
internal const val MAX_WHISPER_THREADS = 8

/** whisper.cpp output is standard UTF-8 and can be cut mid-sequence; malformed bytes become U+FFFD. */
internal fun decodeWhisperText(bytes: ByteArray): String = String(bytes, Charsets.UTF_8)

/**
 * [WhisperApi] over [WhisperNativeCalls]: input guards and handle bookkeeping live here, in Kotlin, so a
 * stale or double-freed handle never reaches native code.
 *
 * Handles are monotonic ids mapped to native pointers, so a freed id is never valid again. [free] never
 * waits for a running decode: it closes the handle and aborts the decode, and whichever side finishes
 * last releases the native session.
 */
internal class WhisperBridge(
    private val calls: WhisperNativeCalls,
    private val maxThreads: Int = minOf(Runtime.getRuntime().availableProcessors(), MAX_WHISPER_THREADS),
) : WhisperApi {
    private class Session(val ptr: Long) {
        var busy = false
        var closed = false
    }

    private val lock = Any()
    private val sessions = HashMap<Long, Session>()
    private var nextId = 1L

    override fun load(modelPath: String, threads: Int): Long {
        if (modelPath.isBlank() || !calls.isAvailable()) return 0L
        // Model loading takes seconds; it runs outside the lock so free() of another handle never waits on it.
        val ptr = calls.init(modelPath, threads.coerceIn(1, maxThreads.coerceAtLeast(1)))
        if (ptr == 0L) return 0L
        synchronized(lock) {
            val id = nextId++
            sessions[id] = Session(ptr)
            return id
        }
    }

    override fun transcribe(ctx: Long, pcm16kMono: FloatArray, lang: String, translate: Boolean): String {
        val session = synchronized(lock) {
            val session = requireNotNull(sessions[ctx]) { "Not a live whisper context" }
            check(!session.busy) { "Whisper context is already transcribing" }
            if (pcm16kMono.isEmpty()) return ""
            session.busy = true
            session
        }
        var aborted = false
        val bytes = try {
            calls.transcribe(session.ptr, pcm16kMono, lang, translate)
        } finally {
            synchronized(lock) {
                session.busy = false
                if (session.closed) {
                    aborted = true
                    calls.free(session.ptr)
                }
            }
        }
        if (aborted) throw WhisperException("Transcription aborted: context freed")
        bytes ?: throw WhisperException("whisper_full failed")
        return decodeWhisperText(bytes).trim()
    }

    override fun free(ctx: Long) {
        synchronized(lock) {
            val session = sessions.remove(ctx) ?: return
            session.closed = true
            if (session.busy) calls.abort(session.ptr) else calls.free(session.ptr)
        }
    }
}
