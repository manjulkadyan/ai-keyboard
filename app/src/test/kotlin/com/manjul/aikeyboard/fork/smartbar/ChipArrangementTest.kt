/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.smartbar

import android.app.Application
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import com.manjul.aikeyboard.fork.smartbar.ChipId.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
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

    @Test
    fun `order and pins persist through the settings store`() = runTest {
        val store = FeatureSettingsStore(RuntimeEnvironment.getApplication(), UnconfinedTestDispatcher(testScheduler))
        val order = listOf(MIC, EMOJI, CLIPBOARD, SETTINGS)
        val pins = listOf(CLIPBOARD, EMOJI)
        store.update { it.copy(smartbarOrder = order.map(ChipId::name), smartbarPinned = pins.map(ChipId::name)) }
        assertEquals(order, ChipId.parse(store.current.smartbarOrder))
        assertEquals(pins, ChipId.parse(store.current.smartbarPinned))
    }

    @Test
    fun `parse drops unknown names`() {
        assertEquals(listOf(EMOJI), ChipId.parse(listOf("GONE", "EMOJI")))
    }
}
