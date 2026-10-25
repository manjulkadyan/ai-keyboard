/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTextToSpeech
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TtsManagerTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val created = mutableListOf<TextToSpeech>()

    /** Robolectric's TextToSpeech never calls onInit; the tests do it. */
    private class MissingDataTts(context: Context, listener: OnInitListener) : TextToSpeech(context, listener) {
        override fun isLanguageAvailable(loc: Locale?): Int = LANG_MISSING_DATA
    }

    private fun manager(missingData: Boolean = false, maxLen: Int = 4000) = TtsManager(
        context,
        factory = { ctx, listener ->
            (if (missingData) MissingDataTts(ctx, listener) else TextToSpeech(ctx, listener)).also { created += it }
        },
        maxInputLength = { maxLen },
    )

    private fun init(tts: TextToSpeech, status: Int = TextToSpeech.SUCCESS) =
        shadowOf(tts).onInitListener.onInit(status)

    @After fun tearDown() = ShadowTextToSpeech.reset()

    @Test fun `nothing is created before acquire`() = runTest {
        val m = manager()
        assertTrue(created.isEmpty())
        assertEquals(TtsAvailability.NOT_SUPPORTED, m.availability(Locale.ENGLISH))
        m.speak("hello", Locale.ENGLISH)
        assertTrue(created.isEmpty())
    }

    @Test fun `two acquires share one engine and two releases shut it down once`() {
        val m = manager()
        m.acquire()
        m.acquire()
        assertEquals(1, created.size)
        val tts = created.single()
        m.release()
        assertFalse(shadowOf(tts).isShutdown)
        m.release()
        assertTrue(shadowOf(tts).isShutdown)
        m.release() // extra release is ignored
        assertEquals(1, created.size)
        m.acquire()
        assertEquals(2, created.size, "a new holder after shutdown gets a fresh engine")
    }

    @Test fun `missing voice reports MISSING_DATA and the install intent`() = runTest {
        val m = manager(missingData = true)
        m.acquire()
        init(created.single())
        assertEquals(TtsAvailability.MISSING_DATA, m.availability(Locale.forLanguageTag("ur")))
        assertEquals(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA, m.installVoiceIntent().action)
    }

    @Test fun `installed and unsupported languages`() = runTest {
        ShadowTextToSpeech.addLanguageAvailability(Locale.ENGLISH)
        val m = manager()
        m.acquire()
        init(created.single())
        assertEquals(TtsAvailability.AVAILABLE, m.availability(Locale.ENGLISH))
        assertEquals(TtsAvailability.NOT_SUPPORTED, m.availability(Locale.forLanguageTag("bn")))
    }

    @Test fun `failed init is unavailable`() = runTest {
        val m = manager()
        m.acquire()
        init(created.single(), TextToSpeech.ERROR)
        assertEquals(TtsState.Unavailable, m.state.value)
        assertEquals(TtsAvailability.NOT_SUPPORTED, m.availability(Locale.ENGLISH))
    }

    @Test fun `speak before init waits, then queues every chunk`() {
        val m = manager(maxLen = 16 + 20)
        m.acquire()
        m.speak("First sentence. Second sentence.", Locale.ENGLISH)
        val shadow = shadowOf(created.single())
        assertTrue(shadow.spokenTextList.isEmpty())
        assertEquals(TtsState.Initializing, m.state.value)
        init(created.single())
        assertEquals(listOf("First sentence. ", "Second sentence."), shadow.spokenTextList)
        assertEquals(TextToSpeech.QUEUE_ADD, shadow.queueMode)
        assertEquals(TtsState.Speaking(0, 2), m.state.value)
    }

    @Test fun `progress listener moves through the chunks and ends idle`() {
        val m = manager(maxLen = 16 + 20)
        m.acquire()
        init(created.single())
        m.speak("First sentence. Second sentence.", Locale.ENGLISH)
        val listener = shadowOf(created.single()).utteranceProgressListener
        val ids = (0..1).map { "tts-1-$it" }
        listener.onStart(ids[1])
        assertEquals(TtsState.Speaking(1, 2), m.state.value)
        listener.onDone(ids[1])
        assertEquals(TtsState.Idle, m.state.value)
    }

    @Test fun `stop ignores callbacks of the stopped utterance`() {
        val m = manager()
        m.acquire()
        init(created.single())
        m.speak("Hello.", Locale.ENGLISH)
        m.stop()
        assertEquals(TtsState.Idle, m.state.value)
        assertTrue(shadowOf(created.single()).isStopped)
        shadowOf(created.single()).utteranceProgressListener.onStart("tts-1-0")
        assertEquals(TtsState.Idle, m.state.value)
    }
}
