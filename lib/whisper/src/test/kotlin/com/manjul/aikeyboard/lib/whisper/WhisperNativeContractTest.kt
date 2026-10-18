/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.lib.whisper

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The [WhisperApi] contract that [WhisperNative] provides, checked on [WhisperBridge] over a fake
 * native seam (libaikb_whisper.so cannot load on the JVM; native behaviour is on the device checklist).
 */
class WhisperNativeContractTest {

    private class FakeCalls(
        var available: Boolean = true,
        var initResult: Long = 42L,
        var transcribeResult: ByteArray? = " hello world".toByteArray(),
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

        override fun transcribe(ptr: Long, pcm: FloatArray, lang: String, translate: Boolean): ByteArray? {
            calls += "transcribe:$ptr"
            lastLang = lang
            lastTranslate = translate
            return transcribeResult
        }

        override fun abort(ptr: Long) {
            calls += "abort:$ptr"
        }

        override fun free(ptr: Long) {
            calls += "free:$ptr"
        }
    }

    /** A decode that runs until aborted (or released by the test), like whisper_full polling abort_callback. */
    private class BlockingCalls : WhisperNativeCalls {
        val inside = CountDownLatch(1)
        val aborted = CountDownLatch(1)
        val frees = mutableListOf<Long>()

        override fun isAvailable() = true
        override fun init(modelPath: String, threads: Int) = 7L
        override fun transcribe(ptr: Long, pcm: FloatArray, lang: String, translate: Boolean): ByteArray? {
            inside.countDown()
            return if (aborted.await(5, TimeUnit.SECONDS)) null else "late".toByteArray()
        }
        override fun abort(ptr: Long) = aborted.countDown()
        override fun free(ptr: Long) {
            synchronized(frees) { frees += ptr }
        }
    }

    private val pcm = FloatArray(16_000)

    @Test
    fun `load returns a non-zero handle`() {
        val bridge = WhisperBridge(FakeCalls())
        assertNotEquals(0L, bridge.load("/models/base.bin", threads = 4))
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
    fun `WhisperNative rejects a blank path before loading the native library`() {
        assertEquals(0L, WhisperNative.load("", threads = 1))
    }

    @Test
    fun `load clamps the thread count to at least 1`() {
        val calls = FakeCalls()
        WhisperBridge(calls, maxThreads = 4).load("/models/base.bin", threads = 0)
        assertEquals(1, calls.lastThreads)
    }

    @Test
    fun `load clamps the thread count to maxThreads`() {
        val calls = FakeCalls()
        WhisperBridge(calls, maxThreads = 4).load("/models/base.bin", threads = 64)
        assertEquals(4, calls.lastThreads)
    }

    @Test
    fun `default maxThreads is at most MAX_WHISPER_THREADS`() {
        val calls = FakeCalls()
        WhisperBridge(calls).load("/models/base.bin", threads = 1_000)
        assertEquals(minOf(Runtime.getRuntime().availableProcessors(), MAX_WHISPER_THREADS), calls.lastThreads)
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
    fun `transcribe passes the native pointer, language and translate through and trims the result`() {
        val calls = FakeCalls()
        val bridge = WhisperBridge(calls)
        val ctx = bridge.load("/models/base.bin", threads = 4)

        assertEquals("hello world", bridge.transcribe(ctx, pcm, "hi", translate = true))
        assertEquals("transcribe:42", calls.calls.last())
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
    fun `a 4-byte emoji and Devanagari decode intact`() {
        val text = "नमस्ते 🎵"
        val calls = FakeCalls(transcribeResult = text.toByteArray(Charsets.UTF_8))
        val bridge = WhisperBridge(calls)

        assertEquals(text, bridge.transcribe(bridge.load("/models/base.bin", threads = 4), pcm, "hi"))
    }

    @Test
    fun `a truncated multi-byte sequence becomes U+FFFD instead of failing`() {
        // "ab" followed by the first two bytes of the 3-byte UTF-8 encoding of U+0928.
        val bytes = byteArrayOf('a'.code.toByte(), 'b'.code.toByte(), 0xE0.toByte(), 0xA4.toByte())
        val calls = FakeCalls(transcribeResult = bytes)
        val bridge = WhisperBridge(calls)

        assertEquals("ab�", bridge.transcribe(bridge.load("/models/base.bin", threads = 4), pcm, "hi"))
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
    fun `a freed handle stays invalid after the same native pointer is loaded again`() {
        val calls = FakeCalls(initResult = 42L)
        val bridge = WhisperBridge(calls)
        val first = bridge.load("/models/base.bin", threads = 4)
        bridge.free(first)

        val second = bridge.load("/models/base.bin", threads = 4)

        assertNotEquals(first, second)
        assertThrows<IllegalArgumentException> { bridge.transcribe(first, pcm, "en") }
        assertEquals("hello world", bridge.transcribe(second, pcm, "en"))
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
    fun `free during a running decode returns promptly, aborts it and frees once`() {
        val calls = BlockingCalls()
        val bridge = WhisperBridge(calls)
        val ctx = bridge.load("/models/base.bin", threads = 4)
        var outcome: Throwable? = null
        val worker = Thread {
            try {
                bridge.transcribe(ctx, pcm, "en")
            } catch (t: Throwable) {
                outcome = t
            }
        }.apply { start() }
        calls.inside.await()

        val started = System.nanoTime()
        bridge.free(ctx)
        val freeMillis = (System.nanoTime() - started) / 1_000_000
        worker.join(5_000)

        assertTrue(freeMillis < 1_000, "free blocked for $freeMillis ms")
        assertEquals(0L, calls.aborted.count, "decode was not aborted")
        assertIs<WhisperException>(outcome)
        assertEquals(listOf(7L), calls.frees)
        assertThrows<IllegalArgumentException> { bridge.transcribe(ctx, pcm, "en") }
    }

    @Test
    fun `a second transcribe on a busy handle throws instead of waiting`() {
        val calls = BlockingCalls()
        val bridge = WhisperBridge(calls)
        val ctx = bridge.load("/models/base.bin", threads = 4)
        val worker = Thread { runCatching { bridge.transcribe(ctx, pcm, "en") } }.apply { start() }
        calls.inside.await()

        assertThrows<IllegalStateException> { bridge.transcribe(ctx, pcm, "en") }

        bridge.free(ctx)
        worker.join(5_000)
        assertEquals(listOf(7L), calls.frees)
    }
}
