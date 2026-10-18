/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.voice.platform

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.manjul.aikeyboard.core.voice.EngineKind
import com.manjul.aikeyboard.core.voice.SpeechEngine

/**
 * Recognizer extras. [sdkInt] is a parameter so the API 30 / 33 differences are testable.
 * Formatting and segmented sessions exist from API 33; [segmented] is decided by the caller
 * (see SpeechSession.isSegmentedSupported).
 */
internal fun buildRecognizerIntent(lang: String, sdkInt: Int, segmented: Boolean): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
            if (segmented) putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, true)
        }
    }

/**
 * Engine over android.speech.SpeechRecognizer. All recognizer calls run on the main thread.
 *
 * Segmented mode (API 33+): onSegmentResults maps to onSegment; onEndOfSegmentedSession maps to onFinal("")
 * (the text was already delivered through the segments, so nothing is committed twice, and the session still
 * sees the end of the recognizer run and can restart). Transcript text is never logged.
 */
class PlatformSpeechEngine(
    private val context: Context,
    private val onDevice: Boolean,
    private val segmented: Boolean = false,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) : SpeechEngine {
    override val kind: EngineKind = if (onDevice) EngineKind.SYSTEM_ON_DEVICE else EngineKind.SYSTEM_DEFAULT
    override val supportsPartials: Boolean = true

    private var recognizer: SpeechRecognizer? = null
    private var destroyed = false

    override fun start(lang: String, listener: SpeechEngine.Listener) = onMain {
        if (destroyed) return@onMain
        recognizer?.destroy()
        val r = if (onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        r.setRecognitionListener(Bridge(listener))
        recognizer = r
        r.startListening(buildRecognizerIntent(lang, Build.VERSION.SDK_INT, segmented))
    }

    override fun stop() = onMain { recognizer?.stopListening() }

    override fun cancel() = onMain { recognizer?.cancel() }

    override fun destroy() = onMain {
        destroyed = true
        recognizer?.destroy()
        recognizer = null
    }

    private inline fun onMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == mainHandler.looper) block() else mainHandler.post { block() }
    }

    private class Bridge(private val l: SpeechEngine.Listener) : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = l.onReady()
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = l.onRms(rmsdB)
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = l.onEndOfSpeech()
        override fun onError(error: Int) = l.onError(error)
        override fun onResults(results: Bundle?) = l.onFinal(results.firstText())
        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults.firstText()
            if (text.isNotEmpty()) l.onPartial(text)
        }
        override fun onSegmentResults(segmentResults: Bundle) = l.onSegment(segmentResults.firstText())
        override fun onEndOfSegmentedSession() = l.onFinal("")
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        private fun Bundle?.firstText(): String =
            this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
    }
}
