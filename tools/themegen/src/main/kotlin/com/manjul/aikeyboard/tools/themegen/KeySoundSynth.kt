/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.tools.themegen

import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Synthesises the four key sounds (S4 Task 10) as 48 kHz mono 16-bit PCM WAV of at most 40 ms. Every sound is an
 * original work: a short decaying tone (with a pitch drop for `pop`) or seeded noise through a resonator (`wood`).
 * The output depends only on the constants here, so a re-run writes the same bytes.
 */
object KeySoundSynth {
    const val SAMPLE_RATE = 48_000
    const val MAX_MS = 40
    val NAMES: List<String> = listOf("soft", "crisp", "wood", "pop")

    private class Voice(val ms: Int, val gain: Double, val render: (t: Double, i: Int) -> Double)

    private val voices: Map<String, Voice> = mapOf(
        // A low, round thump.
        "soft" to Voice(ms = 34, gain = 0.55) { t, _ ->
            exp(-t * 140.0) * (sin(2 * PI * 420.0 * t) + 0.35 * sin(2 * PI * 840.0 * t))
        },
        // A short, bright tick.
        "crisp" to Voice(ms = 18, gain = 0.5) { t, _ ->
            exp(-t * 380.0) * (sin(2 * PI * 2_600.0 * t) + 0.5 * sin(2 * PI * 4_100.0 * t))
        },
        // A knock: seeded noise burst plus a damped resonance.
        "wood" to Voice(ms = 30, gain = 0.6) { t, i ->
            val noise = Lcg.at(i)
            exp(-t * 600.0) * noise * 0.6 + exp(-t * 170.0) * sin(2 * PI * 760.0 * t)
        },
        // A bubble: the pitch falls quickly from 1.4 kHz to 600 Hz.
        "pop" to Voice(ms = 26, gain = 0.55) { t, _ ->
            val phase = 2 * PI * (600.0 * t + (800.0 / 180.0) * (1 - exp(-t * 180.0)))
            exp(-t * 160.0) * sin(phase)
        },
    )

    /** A fixed linear congruential generator, so the `wood` noise is the same on every run and JVM. */
    private object Lcg {
        private val table: DoubleArray = DoubleArray(SAMPLE_RATE * MAX_MS / 1000).also { out ->
            var x = 0x2545F491L
            for (i in out.indices) {
                x = (x * 1_103_515_245L + 12_345L) and 0x7FFFFFFFL
                out[i] = x / 1_073_741_823.5 - 1.0
            }
        }

        fun at(i: Int): Double = table[i]
    }

    fun samples(name: String): ShortArray {
        val voice = requireNotNull(voices[name]) { "unknown key sound $name" }
        require(voice.ms <= MAX_MS)
        val n = SAMPLE_RATE * voice.ms / 1000
        val attack = SAMPLE_RATE / 2000 // 0.5 ms fade-in, no click at the start
        val release = SAMPLE_RATE / 500 // 2 ms fade-out to exact silence
        return ShortArray(n) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val fadeIn = if (i < attack) i.toDouble() / attack else 1.0
            val fadeOut = if (i >= n - release) (n - 1 - i).toDouble() / release else 1.0
            val v = voice.render(t, i) * voice.gain * fadeIn * fadeOut
            (v.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).roundToInt().toShort()
        }
    }

    fun wav(name: String): ByteArray {
        val pcm = samples(name)
        val dataBytes = pcm.size * 2
        val out = ByteArrayOutputStream(44 + dataBytes)
        fun int(v: Int) = repeat(4) { out.write((v ushr (8 * it)) and 0xFF) }
        fun short(v: Int) = repeat(2) { out.write((v ushr (8 * it)) and 0xFF) }
        out.write("RIFF".toByteArray())
        int(36 + dataBytes)
        out.write("WAVEfmt ".toByteArray())
        int(16)
        short(1) // PCM
        short(1) // mono
        int(SAMPLE_RATE)
        int(SAMPLE_RATE * 2) // byte rate
        short(2) // block align
        short(16) // bits per sample
        out.write("data".toByteArray())
        int(dataBytes)
        for (s in pcm) short(s.toInt())
        return out.toByteArray()
    }

    /** Writes `key_<name>.wav` for the four [NAMES] into [rawDir]. No other file in [rawDir] is touched. */
    fun write(rawDir: File) {
        rawDir.mkdirs()
        for (name in NAMES) File(rawDir, "key_$name.wav").writeBytes(wav(name))
    }
}
