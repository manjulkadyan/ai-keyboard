/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translate

import android.app.ActivityManager
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LowTierDeviceTest {
    private val am = RuntimeEnvironment.getApplication().getSystemService(ActivityManager::class.java)

    private fun ram(gib: Long) {
        shadowOf(am).setMemoryInfo(ActivityManager.MemoryInfo().apply { totalMem = gib shl 30 })
    }

    @Test fun lowRamFlagIsLowTier() {
        ram(8)
        shadowOf(am).setIsLowRamDevice(true)
        assertTrue(LowTierDevice.isLowTier(am))
    }

    @Test fun lessThan4GibIsLowTier() {
        shadowOf(am).setIsLowRamDevice(false)
        ram(3)
        assertTrue(LowTierDevice.isLowTier(am))
    }

    @Test fun sixGibIsNotLowTier() {
        shadowOf(am).setIsLowRamDevice(false)
        ram(6)
        assertFalse(LowTierDevice.isLowTier(am))
    }
}
