/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.themes

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.annotation.VisibleForTesting

/**
 * Plays the bundled key sounds (S4 Task 10). The [SoundPool] is built on the first [play] of a bundled sound, and
 * each sample is loaded on its first use; that first press plays as soon as the sample is ready. At most
 * [MAX_STREAMS] clicks sound at once: a new one stops the oldest. [KeySound.OFF] and [KeySound.SYSTEM] are not
 * handled here (the caller keeps the platform click for SYSTEM). Thread-safe; call [release] when done. After
 * [release], [play] does nothing, so a late key press cannot build a new pool that nobody frees.
 */
class KeySoundPlayer(context: Context) {
    companion object {
        const val MAX_STREAMS = 4
    }

    private val context = context.applicationContext
    private var pool: SoundPool? = null
    private val soundIds = HashMap<KeySound, Int>()
    private val loaded = HashSet<Int>()
    /** Volume of a first press that waits for its sample, by sample id. */
    private val pendingPlays = HashMap<Int, Float>()
    private val streams = ArrayDeque<Int>()
    private var released = false

    @get:VisibleForTesting
    val hasPool: Boolean @Synchronized get() = pool != null

    @get:VisibleForTesting
    val poolForTest: SoundPool? @Synchronized get() = pool

    @get:VisibleForTesting
    val activeStreams: Int @Synchronized get() = streams.size

    fun handles(sound: KeySound): Boolean = sound.rawRes != null

    @Synchronized
    fun play(sound: KeySound, volume: Float) {
        if (released) return
        val res = sound.rawRes ?: return
        val pool = pool ?: newPool().also { pool = it }
        val v = volume.coerceIn(0f, 1f)
        val id = soundIds.getOrPut(sound) { pool.load(context, res, 1) }
        if (id == 0) return
        if (id in loaded) start(pool, id, v) else pendingPlays[id] = v
    }

    @Synchronized
    fun release() {
        released = true
        pool?.release()
        pool = null
        soundIds.clear()
        loaded.clear()
        pendingPlays.clear()
        streams.clear()
    }

    private fun newPool(): SoundPool {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        return SoundPool.Builder().setMaxStreams(MAX_STREAMS).setAudioAttributes(attributes).build().apply {
            setOnLoadCompleteListener { p, sampleId, status -> onLoaded(p, sampleId, status) }
        }
    }

    @Synchronized
    private fun onLoaded(p: SoundPool, sampleId: Int, status: Int) {
        if (p !== pool) return
        val pending = pendingPlays.remove(sampleId)
        if (status != 0) {
            // A failed load is retried on the next press.
            soundIds.entries.removeAll { it.value == sampleId }
            return
        }
        loaded += sampleId
        if (pending != null) start(p, sampleId, pending)
    }

    private fun start(p: SoundPool, sampleId: Int, volume: Float) {
        if (streams.size >= MAX_STREAMS) p.stop(streams.removeFirst())
        val stream = p.play(sampleId, volume, volume, 1, 0, 1f)
        if (stream != 0) streams.addLast(stream)
    }
}
