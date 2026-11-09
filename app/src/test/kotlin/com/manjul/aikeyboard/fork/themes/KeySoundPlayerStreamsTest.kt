/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.themes

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.manjul.aikeyboard.R
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import kotlin.test.assertEquals

/**
 * A [SoundPool] shadow that, unlike the stock one, gives each play its own stream id and records [stop] calls.
 * It does not extend the stock shadow: that class needs `IAudioService` outside the Robolectric sandbox.
 */
@Implements(SoundPool::class)
class StreamTrackingSoundPool {
    private var listener: SoundPool.OnLoadCompleteListener? = null
    private val resBySample = HashMap<Int, Int>()
    private var nextSample = 1
    private var nextStream = 100
    val started = mutableListOf<Int>()
    val stopped = mutableListOf<Int>()

    @Implementation
    fun load(context: Context, resId: Int, priority: Int): Int = nextSample++.also { resBySample[it] = resId }

    @Implementation
    fun setOnLoadCompleteListener(l: SoundPool.OnLoadCompleteListener?) {
        listener = l
    }

    @Suppress("FunctionName")
    @Implementation
    fun _play(soundId: Int, left: Float, right: Float, priority: Int, loop: Int, rate: Float): Int =
        nextStream++.also { started += it }

    @Suppress("FunctionName")
    @Implementation
    fun _play(soundId: Int, left: Float, right: Float, priority: Int, loop: Int, rate: Float, playerIId: Int): Int =
        nextStream++.also { started += it }

    @Implementation
    fun stop(streamId: Int) {
        stopped += streamId
    }

    fun notifyLoaded(pool: SoundPool, resId: Int) {
        val sample = resBySample.entries.first { it.value == resId }.key
        listener?.onLoadComplete(pool, sample, 0)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    application = android.app.Application::class,
    shadows = [StreamTrackingSoundPool::class],
)
class KeySoundPlayerStreamsTest {
    private val player = KeySoundPlayer(RuntimeEnvironment.getApplication())

    @After fun tearDown() = player.release()

    /** `PlayerBase.mAttributes`, the attributes the pool was built with. */
    private fun attributesOf(pool: SoundPool): AudioAttributes {
        var c: Class<*>? = pool.javaClass
        while (c != null) {
            runCatching { c.getDeclaredField("mAttributes") }.getOrNull()?.let {
                it.isAccessible = true
                return it.get(pool) as AudioAttributes
            }
            c = c.superclass
        }
        error("no mAttributes")
    }

    @Test fun `a fifth sounding stream stops the oldest one`() {
        player.play(KeySound.CRISP, 1f)
        val pool = player.poolForTest!!
        val shadow = Shadow.extract<StreamTrackingSoundPool>(pool)
        shadow.notifyLoaded(pool, R.raw.key_crisp)
        repeat(5) { player.play(KeySound.CRISP, 1f) }
        // The queued first press plus five fast ones: 6 streams, and plays 5 and 6 stopped streams 1 and 2.
        assertEquals(6, shadow.started.size)
        assertEquals(listOf(shadow.started[0], shadow.started[1]), shadow.stopped)
        assertEquals(KeySoundPlayer.MAX_STREAMS, player.activeStreams)
        assertEquals(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION, attributesOf(pool).usage)
    }
}
