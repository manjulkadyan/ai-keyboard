/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.lib.whisper

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The [WhisperApi] contract that [WhisperNative] provides, checked on [WhisperBridge] over a fake
 * native seam (libaikb_whisper.so cannot load on the JVM; native behaviour is on the device checklist).
 */
class WhisperNativeContractTest {

    private class FakeCalls(
        var available: Boolean = true,
        var initResult: Long = 42L,
        var transcribeResult: String? = " hello world",
    ) : WhisperNativeCalls {
        val calls = mutableListOf<String>()
        var lastThreads = -1
        var lastLang = ""
        var lastTranslate = false

        override fun isAvailable(): Boolean = available

        override fun init(modelPath: String, threads: Int): Long {
            calls += "init"
            lastThreads = threads
            return initResult
        }

        override fun transcribe(ctx: Long, pcm: FloatArray, lang: String, translate: Boolean): String? {
            calls += "transcribe"
            lastLang = lang
            lastTranslate = translate
            return transcribeResult
        }

        override fun free(ctx: Long) {
            calls += "free:$ctx"
        }
    }

    private val pcm = FloatArray(16_000)

    @Test
    fun `load returns the native handle`() {
        val bridge = WhisperBridge(FakeCalls())
        assertEquals(42L, bridge.load("/models/base.bin", threads = 4))
    }

    @Test
    fun `load failure returns 0 and free of it is a safe no-op`() {
        val calls = FakeCalls(initResult = 0L)
        val bridge = WhisperBridge(calls)

        val ctx = bridge.load("/models/missing.bin", threads = 4)
        bridge.free(ctx)

        assertEquals(0L, ctx)
        assertEquals(listOf("init"), calls.calls)
    }

    @Test
    fun `load returns 0 without calling native when the library is unavailable`() {
        val calls = FakeCalls(available = false)
        assertEquals(0L, WhisperBridge(calls).load("/models/base.bin", threads = 4))
        assertTrue(calls.calls.isEmpty())
    }

    @Test
    fun `load returns 0 without calling native for a blank path`() {
        val calls = FakeCalls()
        assertEquals(0L, WhisperBridge(calls).load(" ", threads = 4))
        assertTrue(calls.calls.isEmpty())
    }

    @Test
    fun `load clamps the thread count to at least 1`() {
        val calls = FakeCalls()
        WhisperBridge(calls).load("/models/base.bin", threads = 0)
        assertEquals(1, calls.lastThreads)
    }

    @Test
    fun `empty PCM returns an empty string without calling native`() {
        val calls = FakeCalls()
        val bridge = WhisperBridge(calls)
        val ctx = bridge.load("/models/base.bin", threads = 4)

        assertEquals("", bridge.transcribe(ctx, FloatArray(0), "en"))
        assertEquals(listOf("init"), calls.calls)
    }

    @Test
    fun `transcribe passes language and translate through and trims the result`() {
        val calls = FakeCalls()
        val bridge = WhisperBridge(calls)
        val ctx = bridge.load("/models/base.bin", threads = 4)

        assertEquals("hello world", bridge.transcribe(ctx, pcm, "hi", translate = true))
        assertEquals("hi", calls.lastLang)
        assertTrue(calls.lastTranslate)
    }

    @Test
    fun `translate defaults to false`() {
        val calls = FakeCalls()
        val bridge = WhisperBridge(calls)
        bridge.transcribe(bridge.load("/models/base.bin", threads = 4), pcm, "en")
        assertEquals(false, calls.lastTranslate)
    }

    @Test
    fun `native transcription failure throws WhisperException`() {
        val calls = FakeCalls(transcribeResult = null)
        val bridge = WhisperBridge(calls)
        val ctx = bridge.load("/models/base.bin", threads = 4)

        assertThrows<WhisperException> { bridge.transcribe(ctx, pcm, "en") }
    }

    @Test
    fun `transcribe with a handle that is not live throws without calling native`() {
        val calls = FakeCalls()
        val bridge = WhisperBridge(calls)
        val ctx = bridge.load("/models/base.bin", threads = 4)
        bridge.free(ctx)

        assertThrows<IllegalArgumentException> { bridge.transcribe(ctx, pcm, "en") }
        assertThrows<IllegalArgumentException> { bridge.transcribe(0L, pcm, "en") }
        assertEquals(listOf("init", "free:42"), calls.calls)
    }

    @Test
    fun `free twice releases the native context once`() {
        val calls = FakeCalls()
        val bridge = WhisperBridge(calls)
        val ctx = bridge.load("/models/base.bin", threads = 4)

        bridge.free(ctx)
        bridge.free(ctx)

        assertEquals(listOf("init", "free:42"), calls.calls)
    }

    @Test
    fun `free waits for a running transcription on the same bridge`() {
        val order = mutableListOf<String>()
        val inside = java.util.concurrent.CountDownLatch(1)
        val calls = object : WhisperNativeCalls {
            override fun isAvailable() = true
            override fun init(modelPath: String, threads: Int) = 7L
            override fun transcribe(ctx: Long, pcm: FloatArray, lang: String, translate: Boolean): String {
                inside.countDown()
                Thread.sleep(200)
                synchronized(order) { order += "transcribe-end" }
                return "ok"
            }
            override fun free(ctx: Long) {
                synchronized(order) { order += "free" }
            }
        }
        val bridge = WhisperBridge(calls)
        val ctx = bridge.load("/models/base.bin", threads = 4)

        val worker = Thread { bridge.transcribe(ctx, pcm, "en") }.apply { start() }
        inside.await()
        bridge.free(ctx)
        worker.join()

        assertEquals(listOf("transcribe-end", "free"), order)
    }

    @Test
    fun `WhisperNative implements WhisperApi`() {
        val api: WhisperApi = WhisperNative
        assertTrue(api is WhisperNative)
    }
}
