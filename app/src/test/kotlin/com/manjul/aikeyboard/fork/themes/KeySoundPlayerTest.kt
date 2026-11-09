/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.themes

import com.manjul.aikeyboard.R
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class KeySoundPlayerTest {
    private val player = KeySoundPlayer(RuntimeEnvironment.getApplication())

    @After fun tearDown() = player.release()

    @Test fun `OFF plays nothing and builds no pool`() {
        player.play(KeySound.OFF, 1f)
        assertFalse(player.hasPool)
        assertEquals(0, player.activeStreams)
    }

    @Test fun `SYSTEM is not handled by the player`() {
        assertFalse(player.handles(KeySound.SYSTEM))
        assertFalse(player.handles(KeySound.OFF))
        assertTrue(player.handles(KeySound.WOOD))
        player.play(KeySound.SYSTEM, 1f)
        assertFalse(player.hasPool)
    }

    @Test fun `the first play loads lazily and plays once the sample is ready`() {
        player.play(KeySound.SOFT, 0.5f)
        assertTrue(player.hasPool)
        val pool = shadowOf(player.poolForTest!!)
        shadowOf(player.poolForTest!!).notifyResourceLoaded(R.raw.key_soft, true)
        player.play(KeySound.SOFT, 0.5f)
        assertTrue(pool.wasResourcePlayed(R.raw.key_soft))
        // The queued first press is not dropped: it plays on load, then the second press.
        assertEquals(2, pool.getResourcePlaybacks(R.raw.key_soft).size)
        assertFalse(pool.wasResourcePlayed(R.raw.key_crisp))
    }

    @Test fun `five fast plays keep 4 streams`() {
        player.play(KeySound.CRISP, 1f)
        shadowOf(player.poolForTest!!).notifyResourceLoaded(R.raw.key_crisp, true)
        repeat(5) { player.play(KeySound.CRISP, 1f) }
        // The first press plays once its sample is ready, then the five fast ones.
        assertEquals(6, shadowOf(player.poolForTest!!).getResourcePlaybacks(R.raw.key_crisp).size)
        assertEquals(KeySoundPlayer.MAX_STREAMS, player.activeStreams)
    }

    @Test fun `play after release does nothing and builds no new pool`() {
        player.play(KeySound.POP, 1f)
        assertTrue(player.hasPool)
        player.release()
        player.play(KeySound.POP, 1f)
        assertFalse(player.hasPool)
        assertEquals(0, player.activeStreams)
    }

    @Test fun `every wire value maps back and an unknown one falls back to system`() {
        for (s in KeySound.entries) assertEquals(s, KeySound.fromWire(s.wire))
        assertEquals(KeySound.SYSTEM, KeySound.fromWire("loud"))
        assertEquals(null, KeySound.SYSTEM.rawRes)
        assertEquals(R.raw.key_pop, KeySound.POP.rawRes)
    }
}
