/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.stickers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** The in-panel notice replaces the suppressed toast (DT-Q.3). */
@OptIn(ExperimentalCoroutinesApi::class)
class StickerNoticeTest {
    @Test
    fun `a notice shows and then expires`() = runTest {
        val notice = StickerNotice(this, durationMs = 1000)
        assertNull(notice.message.value)
        notice.show(42)
        runCurrent()
        assertEquals(42, notice.message.value)
        advanceTimeBy(1001)
        runCurrent()
        assertNull(notice.message.value)
    }

    @Test
    fun `a newer notice restarts the timer`() = runTest {
        val notice = StickerNotice(this, durationMs = 1000)
        notice.show(1)
        advanceTimeBy(800)
        notice.show(2)
        advanceTimeBy(800)
        runCurrent()
        assertEquals(2, notice.message.value)
        advanceTimeBy(300)
        runCurrent()
        assertNull(notice.message.value)
    }

    @Test
    fun `clear removes the notice at once`() = runTest {
        val notice = StickerNotice(this, durationMs = 1000)
        notice.show(1)
        notice.clear()
        assertNull(notice.message.value)
    }
}
