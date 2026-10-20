/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.voice.whisper

import android.content.ComponentCallbacks2
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class WhisperIdleUnloaderTest {
    private var frees = 0

    private fun TestScope.unloader() =
        WhisperIdleUnloader(clock = { currentTime }, scope = backgroundScope, free = { frees++ })

    @Test fun freesAfterSixtySecondsIdleNotBefore() = runTest {
        val u = unloader()
        u.touch()
        advanceTimeBy(59_000)
        runCurrent()
        assertEquals(0, frees)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, frees)
        advanceTimeBy(120_000)
        assertEquals(1, frees)
    }

    @Test fun touchResetsTheIdleWindow() = runTest {
        val u = unloader()
        u.touch()
        advanceTimeBy(30_000)
        u.touch()
        advanceTimeBy(59_000)
        runCurrent()
        assertEquals(0, frees)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, frees)
    }

    @Suppress("DEPRECATION")
    @Test fun trimMemoryRunningLowFreesImmediately() = runTest {
        val u = unloader()
        u.touch()
        u.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        assertEquals(1, frees)
        // The pending idle free was cancelled: nothing is loaded any more.
        advanceTimeBy(120_000)
        assertEquals(1, frees)
    }

    /** R38: RUNNING_LOW, RUNNING_CRITICAL and BACKGROUND or above free; UI_HIDDEN (settings backgrounded) does not. */
    @Suppress("DEPRECATION")
    @Test fun onlyRunningLowCriticalAndBackgroundOrAboveFree() = runTest {
        val u = unloader()
        u.touch()
        u.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE)
        u.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        assertEquals(0, frees)
        u.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        assertEquals(1, frees)
        u.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        assertEquals(2, frees)
        u.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
        assertEquals(3, frees)
        u.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_MODERATE)
        u.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
        assertEquals(5, frees)
    }

    @Test fun noFreeWithoutATouch() = runTest {
        unloader()
        advanceTimeBy(120_000)
        assertEquals(0, frees)
    }
}
