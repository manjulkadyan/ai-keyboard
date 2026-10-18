/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.lib.whisper

/** JNI entry points of libaikb_whisper.so (src/main/cpp/whisper_jni.cpp). Kept by consumer-rules.pro. */
internal object WhisperJni : WhisperNativeCalls {
    private val loaded: Boolean by lazy {
        try {
            System.loadLibrary("aikb_whisper")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }

    override fun isAvailable(): Boolean = loaded

    override fun init(modelPath: String, threads: Int): Long = nativeInit(modelPath, threads)

    override fun transcribe(ctx: Long, pcm: FloatArray, lang: String, translate: Boolean): String? =
        nativeTranscribe(ctx, pcm, lang, translate)

    override fun free(ctx: Long) = nativeFree(ctx)

    @JvmStatic
    external fun nativeInit(modelPath: String, threads: Int): Long

    @JvmStatic
    external fun nativeTranscribe(ctx: Long, pcm: FloatArray, lang: String, translate: Boolean): String?

    @JvmStatic
    external fun nativeFree(ctx: Long)
}
