/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.voice.platform

import android.content.Context
import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioFocusHolderTest {
    private val audioManager = RuntimeEnvironment.getApplication().getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Test
    fun acquireThenReleaseAbandonsFocus() {
        val holder = AudioFocusHolder(audioManager)
        assertTrue(holder.acquire())
        val shadow = shadowOf(audioManager)
        assertNotNull(shadow.lastAudioFocusRequest)
        assertEquals(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, shadow.lastAudioFocusRequest.audioFocusRequest.focusGain)
        holder.release()
        assertNotNull(shadow.lastAbandonedAudioFocusRequest)
    }

    @Test
    fun doubleReleaseIsSafe() {
        val holder = AudioFocusHolder(audioManager)
        holder.acquire()
        holder.release()
        val first = shadowOf(audioManager).lastAbandonedAudioFocusRequest
        // Another holder abandons in between; a second abandon by the first would overwrite this.
        val other = AudioFocusHolder(audioManager)
        other.acquire()
        other.release()
        val second = shadowOf(audioManager).lastAbandonedAudioFocusRequest
        assertNotSame(first, second)
        holder.release()
        assertSame(second, shadowOf(audioManager).lastAbandonedAudioFocusRequest)
        assertFalse(holder.isHeld)
    }
}
