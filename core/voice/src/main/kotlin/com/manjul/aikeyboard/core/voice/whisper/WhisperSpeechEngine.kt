/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.voice.whisper

import com.manjul.aikeyboard.core.voice.EngineKind
import com.manjul.aikeyboard.core.voice.SpeechEngine
import kotlin.math.log10
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Path of the Whisper model to use, or null when none is downloaded. */
fun interface WhisperModelSource {
    fun readyModelPath(): String?
}

/**
 * Batch on-device dictation with whisper.cpp: [start] records 16 kHz mono PCM into memory (at most
 * [MAX_SAMPLES], 60 s, after which recording stops as if [stop] was called), [stop] transcribes the whole
 * utterance on [transcribeDispatcher] and delivers one onFinal. There are no partials.
 *
 * Methods and listener callbacks run on [scope]'s thread (the main thread in the IME). Every failure becomes
 * onError and never a crash. The audio buffer is zeroed after transcription or cancel, and neither audio nor
 * text is ever logged. [cancel] and [destroy] abort a running transcription and drop its late result.
 */
class WhisperSpeechEngine(
    private val contexts: WhisperContexts,
    private val models: WhisperModelSource,
    private val recorder: PcmRecorder,
    private val scope: CoroutineScope,
    private val idle: WhisperIdleUnloader,
    private val transcribeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SpeechEngine {
    override val kind: EngineKind = EngineKind.WHISPER
    override val supportsPartials: Boolean = false

    private enum class Phase { IDLE, RECORDING, TRANSCRIBING }

    private var phase = Phase.IDLE
    private var destroyed = false
    private var listener: SpeechEngine.Listener? = null
    private var lang = ""
    private var modelPath = ""
    private var transcribeJob: Job? = null

    /** Bumped on every start and cancel; callbacks from an older run are dropped. */
    @Volatile private var generation = 0

    private val bufferLock = Any()
    private var pcm = EMPTY
    private var count = 0
    private var capped = false

    override fun start(lang: String, listener: SpeechEngine.Listener) {
        if (destroyed) return
        if (phase != Phase.IDLE) cancel()
        val path = models.readyModelPath()
        if (path == null) {
            listener.onError(ERROR_LANGUAGE_UNAVAILABLE)
            return
        }
        val gen = ++generation
        this.listener = listener
        this.lang = lang
        modelPath = path
        clearBuffer()
        val started = try {
            recorder.start(
                onSamples = { samples, n -> onSamples(gen, samples, n) },
                onFailure = { scope.launch { onMicLost(gen) } },
            )
        } catch (e: SecurityException) {
            listener.onError(ERROR_INSUFFICIENT_PERMISSIONS)
            return
        }
        if (!started) {
            listener.onError(ERROR_AUDIO)
            return
        }
        phase = Phase.RECORDING
        idle.touch()
        listener.onReady()
        scope.launch(transcribeDispatcher) { contexts.preload(path) }
    }

    override fun stop() {
        if (phase == Phase.RECORDING) transcribe()
    }

    override fun cancel() {
        when (phase) {
            Phase.RECORDING -> recorder.stop()
            Phase.TRANSCRIBING -> {
                transcribeJob?.cancel()
                contexts.abort(this)
            }
            Phase.IDLE -> Unit
        }
        generation++
        phase = Phase.IDLE
        transcribeJob = null
        clearBuffer()
    }

    override fun destroy() {
        cancel()
        destroyed = true
        listener = null
    }

    /** Capture thread. */
    private fun onSamples(gen: Int, samples: FloatArray, n: Int) {
        val reachedCap: Boolean
        synchronized(bufferLock) {
            if (gen != generation || capped) return
            val take = minOf(n, MAX_SAMPLES - count)
            ensureCapacity(count + take)
            samples.copyInto(pcm, destinationOffset = count, startIndex = 0, endIndex = take)
            count += take
            reachedCap = count >= MAX_SAMPLES
            capped = reachedCap
        }
        val db = rmsDb(samples, n)
        idle.touch()
        scope.launch {
            if (gen != generation || phase != Phase.RECORDING) return@launch
            listener?.onRms(db)
            if (reachedCap) transcribe()
        }
    }

    /** The microphone went away mid-recording: report ERROR_AUDIO and drop what was heard. */
    private fun onMicLost(gen: Int) {
        if (gen != generation || phase != Phase.RECORDING) return
        val l = listener
        cancel()
        l?.onError(ERROR_AUDIO)
    }

    private fun transcribe() {
        phase = Phase.TRANSCRIBING
        recorder.stop()
        val l = listener ?: return
        l.onEndOfSpeech()
        val audio = synchronized(bufferLock) { pcm.copyOf(count).also { clearBufferLocked() } }
        val gen = generation
        val path = modelPath
        val whisperLang = whisperLanguage(lang)
        transcribeJob = scope.launch {
            val result = try {
                Result.success(
                    withContext(transcribeDispatcher) {
                        try {
                            contexts.transcribe(this@WhisperSpeechEngine, path, audio, whisperLang)
                        } finally {
                            audio.fill(0f)
                        }
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                // WhisperException, IllegalArgumentException, IllegalStateException; the message is not kept.
                Result.failure(e)
            }
            idle.touch()
            if (gen != generation) return@launch
            phase = Phase.IDLE
            transcribeJob = null
            result.fold(
                onSuccess = { l.onFinal(cleanTranscript(it)) },
                onFailure = { l.onError(ERROR_CLIENT) },
            )
        }
    }

    private fun ensureCapacity(needed: Int) {
        if (needed <= pcm.size) return
        val grown = pcm.copyOf(maxOf(needed, minOf(MAX_SAMPLES, maxOf(INITIAL_SAMPLES, pcm.size * 2))))
        pcm.fill(0f)
        pcm = grown
    }

    private fun clearBuffer() = synchronized(bufferLock) { clearBufferLocked() }

    private fun clearBufferLocked() {
        pcm.fill(0f)
        pcm = EMPTY
        count = 0
        capped = false
    }

    internal companion object {
        const val SAMPLE_RATE = 16_000
        const val MAX_SAMPLES = 60 * SAMPLE_RATE
        private const val INITIAL_SAMPLES = 10 * SAMPLE_RATE
        private val EMPTY = FloatArray(0)

        // android.speech.SpeechRecognizer.ERROR_* values, as SpeechSession maps them.
        const val ERROR_AUDIO = 3
        const val ERROR_CLIENT = 5
        const val ERROR_INSUFFICIENT_PERMISSIONS = 9
        const val ERROR_LANGUAGE_UNAVAILABLE = 13

        private val NON_SPEECH = Regex("""\[[^\]]*]""")
        private val SPACES = Regex("""\s+""")

        /** whisper.cpp tags non-speech as "[BLANK_AUDIO]", "[MUSIC]" and so on; those never reach the field. */
        fun cleanTranscript(text: String): String = text.replace(NON_SPEECH, " ").replace(SPACES, " ").trim()

        /** Whisper takes ISO 639-1 codes; "en-US" becomes "en", and Java's legacy codes are mapped. */
        fun whisperLanguage(tag: String): String {
            val language = tag.substringBefore('-').substringBefore('_').lowercase()
            return LEGACY_CODES[language] ?: language
        }

        private val LEGACY_CODES = mapOf("in" to "id", "iw" to "he", "ji" to "yi")

        /** SpeechRecognizer's onRmsChanged scale (about -2..10), so SpeechSession's speech threshold holds. */
        fun rmsDb(samples: FloatArray, n: Int): Float {
            if (n <= 0) return -2f
            var sum = 0.0
            for (i in 0 until n) sum += samples[i] * samples[i]
            val rms = sqrt(sum / n)
            if (rms <= 0.0) return -2f
            return ((20 * log10(rms) + 60) / 6).toFloat().coerceIn(-2f, 10f)
        }
    }
}
