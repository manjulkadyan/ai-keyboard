/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.voice.platform

import android.os.Looper
import android.speech.RecognitionSupport
import android.speech.SpeechRecognizer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSpeechRecognizer
import java.time.Duration

/** The on-device support probe: its recognizer is released late (and once), and only positive answers are cached. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlatformRecognizerCapabilitiesTest {
    private val caps = PlatformRecognizerCapabilities(RuntimeEnvironment.getApplication())
    private val main get() = shadowOf(Looper.getMainLooper())

    private fun support(vararg installed: String): RecognitionSupport =
        RecognitionSupport.Builder().setInstalledOnDeviceLanguages(installed.toList()).build()

    /** Starts a probe and runs it up to the created recognizer. */
    private fun TestScope.probe(lang: String) = async { caps.onDeviceSupports(lang) }.also {
        runCurrent()
        main.idle()
    }

    private fun answer(r: SpeechRecognizer, support: RecognitionSupport) {
        shadowOf(r).triggerSupportResult(support)
        main.idle()
    }

    @Test fun theProbeRecognizerOutlivesItsAnswer() = runTest {
        val result = probe("en-US")
        val r = ShadowSpeechRecognizer.getLatestSpeechRecognizer()
        answer(r, support("en-US"))
        runCurrent()
        assertEquals(true, result.await())
        assertFalse(shadowOf(r).isDestroyed)
        main.idleFor(Duration.ofMillis(3_000))
        assertTrue(shadowOf(r).isDestroyed)
    }

    @Test fun aTimedOutProbeIsAlsoReleasedLate() = runTest {
        val result = probe("en-US")
        val r = ShadowSpeechRecognizer.getLatestSpeechRecognizer()
        advanceTimeBy(1_501)
        assertNull(result.await())
        main.idle()
        // A session recognizer is created right after a timeout; destroying the probe now would end the service.
        assertFalse(shadowOf(r).isDestroyed)
        answer(r, support("en-US")) // a late answer neither crashes nor counts
        main.idleFor(Duration.ofMillis(3_000))
        assertTrue(shadowOf(r).isDestroyed)
    }

    @Test fun aPositiveAnswerIsCached() = runTest {
        val first = probe("en-US")
        val r = ShadowSpeechRecognizer.getLatestSpeechRecognizer()
        answer(r, support("en-US"))
        runCurrent()
        assertEquals(true, first.await())

        assertEquals(true, caps.onDeviceSupports("en-US"))
        assertSame(r, ShadowSpeechRecognizer.getLatestSpeechRecognizer())
    }

    @Test fun aNegativeAnswerIsNotCached() = runTest {
        val first = probe("ur-PK")
        val r = ShadowSpeechRecognizer.getLatestSpeechRecognizer()
        answer(r, support("en-US"))
        runCurrent()
        assertEquals(false, first.await())

        probe("ur-PK")
        assertNotSame(r, ShadowSpeechRecognizer.getLatestSpeechRecognizer())
    }
}
