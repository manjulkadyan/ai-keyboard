/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translate

import android.app.ActivityManager
import android.content.Context
import com.manjul.aikeyboard.core.translate.bar.DebouncePolicy

/**
 * Plan decision 5: a device is low tier when `ActivityManager.isLowRamDevice` is set or it has less than 4 GiB of
 * RAM. Low-tier devices get the longer bar debounce, and the bar starts in Preview mode until the user picks a mode.
 */
object LowTierDevice {
    fun isLowTier(am: ActivityManager): Boolean {
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return DebouncePolicy.forDevice(am.isLowRamDevice, info.totalMem) == DebouncePolicy.LowTier
    }

    fun isLowTier(context: Context): Boolean =
        context.getSystemService(ActivityManager::class.java)?.let(::isLowTier) ?: false
}
