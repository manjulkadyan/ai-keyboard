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

    override fun transcribe(ptr: Long, pcm: FloatArray, lang: String, translate: Boolean): ByteArray? =
        nativeTranscribe(ptr, pcm, lang, translate)

    override fun abort(ptr: Long) = nativeAbort(ptr)

    override fun free(ptr: Long) = nativeFree(ptr)

    @JvmStatic
    external fun nativeInit(modelPath: String, threads: Int): Long

    @JvmStatic
    external fun nativeTranscribe(ptr: Long, pcm: FloatArray, lang: String, translate: Boolean): ByteArray?

    @JvmStatic
    external fun nativeAbort(ptr: Long)

    @JvmStatic
    external fun nativeFree(ptr: Long)
}
