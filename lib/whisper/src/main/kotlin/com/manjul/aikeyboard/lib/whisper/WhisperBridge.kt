/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.lib.whisper

/** The raw native calls, a seam so [WhisperBridge]'s guards are testable on the JVM. */
internal interface WhisperNativeCalls {
    fun isAvailable(): Boolean

    /** Returns the context pointer, or 0 on failure. */
    fun init(modelPath: String, threads: Int): Long

    /** Returns the concatenated segment text, or null if whisper_full failed. */
    fun transcribe(ctx: Long, pcm: FloatArray, lang: String, translate: Boolean): String?

    fun free(ctx: Long)
}

/**
 * [WhisperApi] over [WhisperNativeCalls]: input guards and handle bookkeeping live here, in Kotlin, so a
 * stale or double-freed handle never reaches native code.
 */
internal class WhisperBridge(private val calls: WhisperNativeCalls) : WhisperApi {
    private val lock = Any()
    private val live = HashSet<Long>()

    override fun load(modelPath: String, threads: Int): Long {
        if (modelPath.isBlank() || !calls.isAvailable()) return 0L
        synchronized(lock) {
            val ctx = calls.init(modelPath, threads.coerceAtLeast(1))
            if (ctx != 0L) live += ctx
            return ctx
        }
    }

    override fun transcribe(ctx: Long, pcm16kMono: FloatArray, lang: String, translate: Boolean): String {
        synchronized(lock) {
            require(ctx in live) { "Not a live whisper context" }
            if (pcm16kMono.isEmpty()) return ""
            val text = calls.transcribe(ctx, pcm16kMono, lang, translate)
                ?: throw WhisperException("whisper_full failed")
            return text.trim()
        }
    }

    override fun free(ctx: Long) {
        synchronized(lock) {
            if (live.remove(ctx)) calls.free(ctx)
        }
    }
}
