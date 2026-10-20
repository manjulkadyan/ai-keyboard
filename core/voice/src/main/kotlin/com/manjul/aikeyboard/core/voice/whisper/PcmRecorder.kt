/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.voice.whisper

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.util.concurrent.atomic.AtomicBoolean

/** 16 kHz mono microphone capture for the Whisper engine. Audio stays in memory and never leaves the phone. */
interface PcmRecorder {
    /**
     * Starts capturing. [onSamples] runs on a capture thread with samples in [-1, 1] and the number of valid
     * samples; the array is reused between calls. [onFailure] runs once on the capture thread if the microphone
     * is lost mid-recording (a failed read). Returns false if the microphone cannot be opened.
     *
     * @throws SecurityException if RECORD_AUDIO is not granted.
     */
    fun start(onSamples: (FloatArray, Int) -> Unit, onFailure: () -> Unit): Boolean

    /**
     * Stops capturing without blocking; no [onSamples] or [onFailure] call starts after it returns. Safe to call
     * when not started.
     */
    fun stop()
}

/**
 * [PcmRecorder] over [AudioRecord] (VOICE_RECOGNITION source, so the platform's voice processing applies). The
 * capture thread owns the [AudioRecord] once started: [stop] only signals it, and the thread stops and releases
 * the recorder after its current read (at most one 100 ms chunk), so the caller's thread never waits.
 */
class AudioRecordPcmRecorder : PcmRecorder {
    /** The running capture's flag; each start gets its own so an old thread never sees a newer run's. */
    @Volatile private var running: AtomicBoolean? = null

    // Callers hold RECORD_AUDIO before starting a voice session; a revoked grant surfaces as SecurityException.
    @SuppressLint("MissingPermission")
    override fun start(onSamples: (FloatArray, Int) -> Unit, onFailure: () -> Unit): Boolean {
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
        } catch (e: SecurityException) {
            r.release()
            throw e
        }
        if (r.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            r.release()
            return false
        }
        val flag = AtomicBoolean(true)
        running = flag
        Thread({ capture(r, flag, onSamples, onFailure) }, "aikb-whisper-mic").start()
        return true
    }

    private fun capture(
        r: AudioRecord,
        running: AtomicBoolean,
        onSamples: (FloatArray, Int) -> Unit,
        onFailure: () -> Unit,
    ) {
        val shorts = ShortArray(CHUNK)
        val floats = FloatArray(CHUNK)
        var lost = false
        try {
            while (running.get()) {
                val n = r.read(shorts, 0, CHUNK)
                if (n < 0) {
                    lost = true
                    break
                }
                if (n == 0) continue
                for (i in 0 until n) floats[i] = shorts[i] / 32768f
                if (running.get()) onSamples(floats, n)
            }
        } finally {
            shorts.fill(0)
            floats.fill(0f)
            runCatching { r.stop() }
            r.release()
        }
        // getAndSet: a stop() that raced the failed read wins and nothing is reported.
        if (lost && running.getAndSet(false)) onFailure()
    }

    override fun stop() {
        running?.set(false)
        running = null
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val CHUNK = 1_600
    }
}
