/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.tts

import android.app.Application
import android.content.Context
import android.speech.tts.TextToSpeech
import com.manjul.aikeyboard.core.tts.TtsAvailability
import com.manjul.aikeyboard.core.tts.TtsManager
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
@Config(sdk = [34], application = Application::class)
class ScreenSpeakerTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val created = mutableListOf<TextToSpeech>()
    private val tts = TtsManager(
        context,
        factory = { ctx, listener -> TextToSpeech(ctx, listener).also { created += it } },
        maxInputLength = { 4000 },
    )

    @After fun tearDown() = ShadowTextToSpeech.reset()

    private fun initEngine() = shadowOf(created.single()).onInitListener.onInit(TextToSpeech.SUCCESS)

    @Test fun `stop and release before any speak leave the shared engine alone`() {
        val speaker = ScreenSpeaker(tts)
        speaker.stop()
        speaker.release()
        assertTrue(created.isEmpty())

        tts.acquire() // another holder, for example the Translator screen
        speaker.release()
        assertFalse(shadowOf(created.single()).isShutdown, "a speaker that never spoke must not release")
    }

    @Test fun `speaks in the given locale and acquires the engine once`() = runTest {
        ShadowTextToSpeech.addLanguageAvailability(Locale.ENGLISH)
        ShadowTextToSpeech.addLanguageAvailability(Locale.FRENCH)
        val speaker = ScreenSpeaker(tts)
        tts.acquire()
        initEngine()
        assertEquals(TtsAvailability.AVAILABLE, speaker.speak("first", Locale.ENGLISH))
        assertEquals(TtsAvailability.AVAILABLE, speaker.speak("second", Locale.FRENCH))
        assertEquals(1, created.size)
        assertEquals(listOf("first", "second"), shadowOf(created.single()).spokenTextList)
        assertEquals(TtsAvailability.NOT_SUPPORTED, speaker.speak("third", Locale.forLanguageTag("bn")))
        assertEquals(listOf("first", "second"), shadowOf(created.single()).spokenTextList, "no voice, no speech")

        tts.release() // the other holder goes; the speaker still holds the engine
        assertFalse(shadowOf(created.single()).isShutdown)
        speaker.release()
        assertTrue(shadowOf(created.single()).isShutdown, "the speaker released its one reference")
    }

    @Test fun `a second release does not take another holder's reference`() = runTest {
        val speaker = ScreenSpeaker(tts)
        tts.acquire()
        initEngine()
        speaker.speak("hello", Locale.ENGLISH)
        speaker.release()
        speaker.release()
        assertFalse(shadowOf(created.single()).isShutdown)
    }
}
