/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.themes

import com.manjul.aikeyboard.fork.themes.color.RecentColors
import org.junit.Test
import kotlin.test.assertEquals

class RecentColorsTest {
    @Test fun `a new colour goes to the front`() {
        assertEquals(listOf(3L, 1L, 2L), RecentColors.add(listOf(1L, 2L), 3L))
        assertEquals(listOf(7L), RecentColors.add(emptyList(), 7L))
    }

    @Test fun `a ninth colour drops the oldest`() {
        val eight = (8L downTo 1L).toList()
        assertEquals((9L downTo 2L).toList(), RecentColors.add(eight, 9L))
        assertEquals(RecentColors.MAX, RecentColors.add(eight, 9L).size)
    }

    @Test fun `a repeat moves to the front without a duplicate`() {
        val recents = listOf(5L, 4L, 3L, 2L, 1L)
        assertEquals(listOf(3L, 5L, 4L, 2L, 1L), RecentColors.add(recents, 3L))
        assertEquals(recents, RecentColors.add(recents, 5L))
    }

    @Test fun `a stored list longer than 8 or with duplicates is cleaned`() {
        val stored = listOf(1L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L)
        assertEquals(listOf(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L), RecentColors.add(stored, 0L))
    }
}
