/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.voice.whisper

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/** 16 kHz mono microphone capture for the Whisper engine. Audio stays in memory and never leaves the phone. */
interface PcmRecorder {
    /**
     * Starts capturing. [onSamples] runs on a capture thread with samples in [-1, 1] and the number of valid
     * samples; the array is reused between calls. Returns false if the microphone cannot be opened.
     *
     * @throws SecurityException if RECORD_AUDIO is not granted.
     */
    fun start(onSamples: (FloatArray, Int) -> Unit): Boolean

    /** Stops capturing; no [onSamples] call happens after it returns. Safe to call when not started. */
    fun stop()
}

/** [PcmRecorder] over [AudioRecord] (VOICE_RECOGNITION source, so the platform's voice processing applies). */
class AudioRecordPcmRecorder : PcmRecorder {
    @Volatile private var running = false
    private var record: AudioRecord? = null
    private var thread: Thread? = null

    // Callers hold RECORD_AUDIO before starting a voice session; a revoked grant surfaces as SecurityException.
    @SuppressLint("MissingPermission")
    override fun start(onSamples: (FloatArray, Int) -> Unit): Boolean {
        stop()
        val minBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBytes <= 0) return false
        val r = try {
            val source = MediaRecorder.AudioSource.VOICE_RECOGNITION
            AudioRecord(source, SAMPLE_RATE, CHANNEL, ENCODING, maxOf(minBytes, CHUNK * 4))
        } catch (e: IllegalArgumentException) {
            return false
        }
        if (r.state != AudioRecord.STATE_INITIALIZED) {
            r.release()
            return false
        }
        try {
            r.startRecording()
        } catch (e: IllegalStateException) {
            r.release()
            return false
        }
        if (r.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            r.release()
            return false
        }
        running = true
        record = r
        thread = Thread({ capture(r, onSamples) }, "aikb-whisper-mic").apply { start() }
        return true
    }

    private fun capture(r: AudioRecord, onSamples: (FloatArray, Int) -> Unit) {
        val shorts = ShortArray(CHUNK)
        val floats = FloatArray(CHUNK)
        while (running) {
            val n = r.read(shorts, 0, CHUNK)
            if (n < 0) break
            if (n == 0) continue
            for (i in 0 until n) floats[i] = shorts[i] / 32768f
            if (running) onSamples(floats, n)
        }
        shorts.fill(0)
        floats.fill(0f)
    }

    override fun stop() {
        running = false
        val r = record ?: return
        record = null
        runCatching { r.stop() }
        // A read returns within one chunk (100 ms) once recording stops.
        thread?.join(JOIN_TIMEOUT_MS)
        thread = null
        r.release()
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val CHUNK = 1_600
        const val JOIN_TIMEOUT_MS = 500L
    }
}
