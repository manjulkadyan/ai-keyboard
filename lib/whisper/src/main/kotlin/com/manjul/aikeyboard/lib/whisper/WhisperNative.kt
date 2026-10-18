/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.lib.whisper

/**
 * On-device whisper.cpp speech-to-text. Batch only: the whole utterance goes in, the text comes out.
 * Callers (WhisperSpeechEngine) depend on this interface so tests can inject a fake.
 *
 * Handles are opaque ids; 0 is never a valid context and a freed id never becomes valid again. One context
 * runs one [transcribe] at a time. [free] never blocks on a running [transcribe]: it aborts it (that call then
 * throws [WhisperException]), so it is safe from the main thread (onTrimMemory, idle unload).
 */
interface WhisperApi {
    /**
     * Loads a ggml model file. Returns a context handle, or 0 if the model cannot be loaded (missing or
     * corrupt file, out of memory, native library unavailable on this ABI).
     */
    fun load(modelPath: String, threads: Int): Long

    /**
     * Transcribes 16 kHz mono float PCM in [-1, 1]. [lang] is an ISO 639-1 code (or "auto");
     * [translate] translates to English. Empty [pcm16kMono] returns "" without running the model.
     *
     * @throws IllegalArgumentException if [ctx] is not a live handle from [load].
     * @throws IllegalStateException if [ctx] is already transcribing on another thread.
     * @throws WhisperException if whisper.cpp fails (unknown language, decode error) or [free] aborted the call.
     */
    fun transcribe(ctx: Long, pcm16kMono: FloatArray, lang: String, translate: Boolean = false): String

    /**
     * Releases a context without waiting; a running [transcribe] on it is aborted and releases it on exit.
     * Safe for 0, for a failed [load] and for a handle that is already freed.
     */
    fun free(ctx: Long)
}

/** whisper.cpp failed. The message never contains audio or transcript content. */
class WhisperException(message: String) : RuntimeException(message)

/** The process-wide [WhisperApi] backed by libaikb_whisper.so (loaded on the first [load]). */
object WhisperNative : WhisperApi by WhisperBridge(WhisperJni)
