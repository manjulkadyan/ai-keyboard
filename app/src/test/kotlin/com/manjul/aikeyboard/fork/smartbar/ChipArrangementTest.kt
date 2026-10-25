/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.smartbar

import org.junit.Test
import kotlin.test.assertEquals
import com.manjul.aikeyboard.fork.smartbar.ChipId.*

class ChipArrangementTest {
    @Test
    fun `fourth pin is rejected and the list is unchanged`() {
        val three = listOf(EMOJI, CLIPBOARD, SETTINGS)
        assertEquals(three, ChipArrangement.pin(three, STICKERS))
    }

    @Test
    fun `pins up to three chips in order`() {
        assertEquals(listOf(EMOJI, SETTINGS), ChipArrangement.pin(listOf(EMOJI), SETTINGS))
    }

    @Test
    fun `pinning a pinned chip does not duplicate it`() {
        assertEquals(listOf(EMOJI), ChipArrangement.pin(listOf(EMOJI), EMOJI))
    }

    @Test
    fun `pinning a fixed chip is a no-op`() {
        assertEquals(emptyList(), ChipArrangement.pin(emptyList(), MIC))
        assertEquals(listOf(EMOJI), ChipArrangement.pin(listOf(EMOJI), TRANSLATE))
    }

    @Test
    fun `reorder never moves fixed chips`() {
        val order = listOf(MIC, TRANSLATE, AI, EMOJI, CLIPBOARD, SETTINGS)
        // moving a fixed chip, or onto a fixed slot, changes nothing
        assertEquals(order, ChipArrangement.reorder(order, 0, 3))
        assertEquals(order, ChipArrangement.reorder(order, 1, 4))
        assertEquals(order, ChipArrangement.reorder(order, 3, 0))
        assertEquals(order, ChipArrangement.reorder(order, 3, 1))
    }

    @Test
    fun `reorder moves movable chips and keeps fixed positions`() {
        val order = listOf(MIC, AI, TRANSLATE, EMOJI, CLIPBOARD, SETTINGS)
        val moved = ChipArrangement.reorder(order, 1, 4)
        assertEquals(listOf(MIC, EMOJI, TRANSLATE, CLIPBOARD, AI, SETTINGS), moved)
        assertEquals(MIC, moved[0])
        assertEquals(TRANSLATE, moved[2])
    }

    @Test
    fun `reorder ignores out of range indices`() {
        val order = listOf(MIC, EMOJI)
        assertEquals(order, ChipArrangement.reorder(order, 1, 5))
        assertEquals(order, ChipArrangement.reorder(order, -1, 1))
    }
}
