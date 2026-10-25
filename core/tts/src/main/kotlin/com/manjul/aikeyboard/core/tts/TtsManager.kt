/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.tts

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The process's one `TextToSpeech` (PRD FR-26 AC1, TRD §4.6), shared by the Translator screen and the selection
 * sheet. Ref-counted: the engine is created on the first [acquire] and shut down when the last holder calls
 * [release]; [availability] and [speak] do nothing while nobody holds it. Long text is split by [TtsChunker] and
 * queued with `QUEUE_ADD`; progress comes from an `UtteranceProgressListener`. Spoken text is never logged.
 */
class TtsManager(
    context: Context,
    private val factory: (Context, TextToSpeech.OnInitListener) -> TextToSpeech = ::TextToSpeech,
    private val maxInputLength: () -> Int = TextToSpeech::getMaxSpeechInputLength,
) {
    private val appContext = context.applicationContext
    private val lock = Any()
    private var refs = 0
    private var current: Engine? = null
    private var pending: (() -> Unit)? = null

    // Utterance ids are "tts-<utterance>-<chunk>"; callbacks for an older utterance are ignored.
    private var utterance = 0L
    private var chunkCount = 0

    private val _state = MutableStateFlow<TtsState>(TtsState.Idle)
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private class Engine {
        var tts: TextToSpeech? = null
        var status: Int? = null
        val ready = CompletableDeferred<Boolean>()
    }

    fun acquire() = synchronized(lock) {
        refs++
        if (refs == 1) create()
    }

    fun release() = synchronized(lock) {
        if (refs == 0) return@synchronized
        refs--
        if (refs == 0) shutdown()
    }

    /** [TtsAvailability.NOT_SUPPORTED] when not acquired or the engine failed; waits for init otherwise. */
    suspend fun availability(locale: Locale): TtsAvailability {
        val engine = synchronized(lock) { current } ?: return TtsAvailability.NOT_SUPPORTED
        if (!engine.ready.await()) return TtsAvailability.NOT_SUPPORTED
        val result = synchronized(lock) {
            if (current !== engine) null else engine.tts?.isLanguageAvailable(locale)
        } ?: return TtsAvailability.NOT_SUPPORTED
        return when {
            result >= TextToSpeech.LANG_AVAILABLE -> TtsAvailability.AVAILABLE
            result == TextToSpeech.LANG_MISSING_DATA -> TtsAvailability.MISSING_DATA
            else -> TtsAvailability.NOT_SUPPORTED
        }
    }

    /** Replaces anything being spoken with [text]. Runs once the engine is ready if it is still binding. */
    fun speak(text: String, locale: Locale) = synchronized(lock) {
        val engine = current ?: return@synchronized
        val action = { speakNow(engine, text, locale) }
        when {
            !engine.ready.isCompleted -> pending = action
            engine.tts != null && engine.status == TextToSpeech.SUCCESS -> action()
        }
    }

    fun stop() = synchronized(lock) {
        pending = null
        utterance++
        current?.takeIf { it.status == TextToSpeech.SUCCESS }?.tts?.stop()
        if (_state.value is TtsState.Speaking) _state.value = TtsState.Idle
    }

    /** Opens the platform screen that installs voice data (for [TtsAvailability.MISSING_DATA]). */
    fun installVoiceIntent(): Intent =
        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun create() {
        val engine = Engine()
        current = engine
        _state.value = TtsState.Initializing
        // The platform may call onInit before the constructor returns (no engine installed), so both orders work.
        val tts = factory(appContext) { status ->
            synchronized(lock) {
                engine.status = status
                if (engine.tts != null) onReady(engine)
            }
        }
        engine.tts = tts
        if (engine.status != null) onReady(engine)
    }

    private fun onReady(engine: Engine) {
        if (engine.ready.isCompleted) return
        val ok = engine.status == TextToSpeech.SUCCESS
        engine.ready.complete(ok)
        if (current !== engine) return
        if (ok) {
            engine.tts?.setOnUtteranceProgressListener(progress)
            _state.value = TtsState.Idle
            pending?.invoke()
        } else {
            _state.value = TtsState.Unavailable
        }
        pending = null
    }

    private fun speakNow(engine: Engine, text: String, locale: Locale) {
        val tts = engine.tts ?: return
        tts.stop()
        utterance++
        val chunks = TtsChunker.chunks(text, locale, maxInputLength())
        chunkCount = chunks.size
        if (chunks.isEmpty()) {
            _state.value = TtsState.Idle
            return
        }
        tts.setLanguage(locale)
        chunks.forEachIndexed { i, chunk -> tts.speak(chunk, TextToSpeech.QUEUE_ADD, null, "tts-$utterance-$i") }
        _state.value = TtsState.Speaking(0, chunks.size)
    }

    private fun shutdown() {
        val engine = current ?: return
        current = null
        pending = null
        utterance++
        engine.ready.complete(false)
        engine.tts?.let {
            it.stop()
            it.shutdown()
        }
        _state.value = TtsState.Idle
    }

    /** Parses "tts-<utterance>-<chunk>" and returns the chunk index if it belongs to the current utterance. */
    private fun chunkOf(id: String?): Int? {
        val parts = id?.split('-') ?: return null
        if (parts.size != 3 || parts[1].toLongOrNull() != utterance) return null
        return parts[2].toIntOrNull()
    }

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = synchronized(lock) {
            chunkOf(utteranceId)?.let { _state.value = TtsState.Speaking(it, chunkCount) }
            Unit
        }

        override fun onDone(utteranceId: String?) = synchronized(lock) {
            if (chunkOf(utteranceId) == chunkCount - 1) _state.value = TtsState.Idle
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = synchronized(lock) {
            if (chunkOf(utteranceId) != null) {
                utterance++
                _state.value = TtsState.Idle
            }
        }
    }
}
