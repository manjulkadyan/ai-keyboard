/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.voice.platform

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.manjul.aikeyboard.core.voice.EngineKind
import com.manjul.aikeyboard.core.voice.SpeechEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlatformSpeechEngineTest {
    private val context = RuntimeEnvironment.getApplication()

    private class Recorder : SpeechEngine.Listener {
        val events = mutableListOf<String>()
        override fun onReady() { events += "ready" }
        override fun onRms(db: Float) { events += "rms" }
        override fun onPartial(text: String) { events += "partial:$text" }
        override fun onSegment(text: String) { events += "segment:$text" }
        override fun onFinal(text: String) { events += "final:$text" }
        override fun onEndOfSpeech() { events += "eos" }
        override fun onError(code: Int) { events += "error:$code" }
    }

    private fun results(text: String) = Bundle().apply {
        putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text))
    }

    private fun engine(segmented: Boolean = false) = PlatformSpeechEngine(context, onDevice = false, segmented = segmented, mainHandler = Handler(Looper.getMainLooper()))

    @Test
    fun intentOnApi33HasFormattingAndSegmented() {
        val intent = buildRecognizerIntent("en-US", sdkInt = 33, segmented = true)
        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, intent.action)
        assertTrue(intent.getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false))
        assertTrue(intent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false))
        assertEquals("en-US", intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertEquals(RecognizerIntent.LANGUAGE_MODEL_FREE_FORM, intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL))
        assertEquals(RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY, intent.getStringExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING))
        assertTrue(intent.getBooleanExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, false))
    }

    @Test
    fun intentOnApi33WithoutSegmentedOmitsSegmented() {
        val intent = buildRecognizerIntent("en-US", sdkInt = 33, segmented = false)
        assertFalse(intent.hasExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION))
        assertTrue(intent.hasExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING))
    }

    @Test
    fun intentOnApi30OmitsFormattingAndSegmented() {
        val intent = buildRecognizerIntent("en-US", sdkInt = 30, segmented = true)
        assertFalse(intent.hasExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING))
        assertFalse(intent.hasExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION))
        assertTrue(intent.getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false))
    }

    @Test
    fun onDeviceFactoryFromApi31() {
        assertFalse(useOnDeviceFactory(onDevice = true, sdkInt = 30))
        assertTrue(useOnDeviceFactory(onDevice = true, sdkInt = 31))
        assertTrue(useOnDeviceFactory(onDevice = true, sdkInt = 33))
        assertFalse(useOnDeviceFactory(onDevice = false, sdkInt = 34))
    }

    @Test
    fun onDeviceEngineStartsOnDeviceRecognizer() {
        val engine = PlatformSpeechEngine(context, onDevice = true, segmented = false, mainHandler = Handler(Looper.getMainLooper()))
        engine.start("en-US", Recorder())
        assertEquals(EngineKind.SYSTEM_ON_DEVICE, engine.kind)
        assertEquals("en-US", latest().lastRecognizerIntent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
    }

    private fun latest() = shadowOf(org.robolectric.shadows.ShadowSpeechRecognizer.getLatestSpeechRecognizer())

    @Test
    fun startCreatesRecognizerAndMapsCallbacks() {
        val engine = engine()
        val rec = Recorder()
        engine.start("en-US", rec)
        val shadow = latest()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("en-US", shadow.lastRecognizerIntent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        shadow.triggerOnReadyForSpeech(Bundle())
        shadow.triggerOnPartialResults(results("hel"))
        shadow.triggerOnResults(results("hello"))
        shadow.triggerOnEndOfSpeech()
        shadow.triggerOnError(SpeechRecognizer.ERROR_NO_MATCH)
        assertEquals(listOf("ready", "partial:hel", "final:hello", "eos", "error:${SpeechRecognizer.ERROR_NO_MATCH}"), rec.events)
        assertEquals(EngineKind.SYSTEM_DEFAULT, engine.kind)
    }

    @Test
    fun segmentCallbacksMapToSegmentThenEmptyFinal() {
        val engine = engine(segmented = true)
        val rec = Recorder()
        engine.start("en-US", rec)
        val shadow = latest()
        shadowOf(Looper.getMainLooper()).idle()
        shadow.triggerOnSegmentResults(results("one"))
        shadow.triggerOnEndOfSegmentedSession()
        assertEquals(listOf("segment:one", "final:"), rec.events)
    }

    @Test
    fun destroyIsIdempotentAndDestroysRecognizer() {
        val engine = engine()
        engine.start("en-US", Recorder())
        val shadow = latest()
        shadowOf(Looper.getMainLooper()).idle()
        engine.destroy()
        engine.destroy()
        assertTrue(shadow.isDestroyed)
    }
}
