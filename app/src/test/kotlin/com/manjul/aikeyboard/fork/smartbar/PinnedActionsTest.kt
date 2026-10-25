/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.smartbar

import com.manjul.aikeyboard.ime.keyboard.KeyData
import com.manjul.aikeyboard.ime.smartbar.quickaction.QuickAction
import com.manjul.aikeyboard.ime.smartbar.quickaction.QuickActionArrangement
import com.manjul.aikeyboard.ime.text.key.KeyCode
import com.manjul.aikeyboard.ime.text.keyboard.TextKeyData
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PinnedActionsTest {
    private fun key(d: KeyData) = QuickAction.InsertKey(d)
    private val mic = key(TextKeyData.VOICE_INPUT)
    private val translate = key(TextKeyData.TOGGLE_TRANSLATE_BAR)
    private val emoji = key(TextKeyData.IME_UI_MODE_MEDIA)
    private val clipboard = key(TextKeyData.IME_UI_MODE_CLIPBOARD)
    private val settings = key(TextKeyData.SETTINGS)
    private val makePrivate = key(TextKeyData.TOGGLE_PRIVATE_APP)
    private val undo = key(TextKeyData.UNDO)
    private val redo = key(TextKeyData.REDO)
    private val noop = key(TextKeyData(code = KeyCode.NOOP))
    private val dragMarker = key(TextKeyData(code = KeyCode.DRAG_MARKER))

    @Test
    fun `a 4th pin is refused`() {
        val three = listOf(emoji, clipboard, settings)
        assertFalse(PinnedActions.canPin(three, undo))
        assertTrue(PinnedActions.canPin(listOf(emoji, clipboard), undo))
    }

    @Test
    fun `the private-app action and the editor placeholders do not use a pin`() {
        val dynamic = listOf(emoji, clipboard, makePrivate, noop, dragMarker)
        assertEquals(2, PinnedActions.pinnedCount(dynamic))
        assertTrue(PinnedActions.canPin(dynamic, undo))
        // Exempt, so it can always go back among the dynamic actions.
        assertTrue(PinnedActions.canPin(listOf(emoji, clipboard, settings), makePrivate))
    }

    @Test
    fun `the mic is not pinnable`() {
        assertFalse(PinnedActions.canPin(emptyList(), mic))
    }

    @Test
    fun `the mic cannot be moved to the dynamic or hidden actions`() {
        val moved = QuickActionArrangement(stickyAction = null, dynamicActions = listOf(mic, emoji), hiddenActions = listOf(mic, undo))
        val locked = PinnedActions.lockSticky(moved)
        assertEquals(mic, locked.stickyAction)
        assertEquals(listOf(emoji), locked.dynamicActions)
        assertEquals(listOf(undo), locked.hiddenActions)
    }

    @Test
    fun `another sticky action is replaced by the mic and hidden`() {
        val arrangement = QuickActionArrangement(stickyAction = undo, dynamicActions = listOf(emoji, mic), hiddenActions = listOf(redo))
        val normalized = PinnedActions.normalize(arrangement)
        assertEquals(mic, normalized.stickyAction)
        assertEquals(listOf(translate, emoji), normalized.dynamicActions)
        assertEquals(listOf(undo, redo), normalized.hiddenActions)
    }

    @Test
    fun `a saved arrangement with 5 dynamic actions is cut to 3 pins`() {
        val saved = QuickActionArrangement(
            stickyAction = mic,
            dynamicActions = listOf(emoji, makePrivate, clipboard, settings, undo, redo),
            hiddenActions = listOf(key(TextKeyData.ARROW_UP)),
        )
        val normalized = PinnedActions.normalize(saved)
        // The private-app action does not count and stays last; the extras go to the front of the hidden actions.
        assertEquals(listOf(translate, emoji, clipboard, settings, makePrivate), normalized.dynamicActions)
        assertEquals(listOf(undo, redo, key(TextKeyData.ARROW_UP)), normalized.hiddenActions)
    }

    @Test
    fun `normalize is idempotent`() {
        val saved = QuickActionArrangement(
            stickyAction = clipboard,
            dynamicActions = listOf(mic, makePrivate, emoji, settings, undo, redo, emoji),
            hiddenActions = listOf(mic, clipboard),
        )
        val once = PinnedActions.normalize(saved)
        assertEquals(once, PinnedActions.normalize(once))
    }

    @Test
    fun `translate is fixed first, never pinnable, hidden or counted`() {
        assertFalse(PinnedActions.canPin(emptyList(), translate))
        assertEquals(3, PinnedActions.pinnedCount(listOf(translate, emoji, clipboard, settings)))
        assertTrue(PinnedActions.canPin(listOf(translate, emoji, clipboard), undo))
        val moved = QuickActionArrangement(
            stickyAction = translate,
            dynamicActions = listOf(emoji, clipboard, settings, undo),
            hiddenActions = listOf(translate, redo),
        )
        val normalized = PinnedActions.normalize(moved)
        assertEquals(mic, normalized.stickyAction)
        assertEquals(listOf(translate, emoji, clipboard, settings), normalized.dynamicActions)
        assertEquals(listOf(undo, redo), normalized.hiddenActions)
    }

    @Test
    fun `the default has the mic sticky and translate first`() {
        assertEquals(mic, QuickActionArrangement.Default.stickyAction)
        assertEquals(translate, QuickActionArrangement.Default.dynamicActions.first())
    }

    @Test
    fun `the fork default already follows the rules`() {
        assertEquals(QuickActionArrangement.Default, PinnedActions.normalize(QuickActionArrangement.Default))
    }

    @Test
    fun `stored arrangements are normalized when read`() {
        val saved = QuickActionArrangement(
            stickyAction = emoji,
            dynamicActions = listOf(clipboard, settings, undo, redo, mic),
            hiddenActions = emptyList(),
        )
        val read = QuickActionArrangement.Serializer.deserialize(QuickActionArrangement.Serializer.serialize(saved))
        assertEquals(mic, read.stickyAction)
        assertEquals(listOf(translate, clipboard, settings, undo), read.dynamicActions)
        assertEquals(listOf(redo, emoji), read.hiddenActions)
    }
}
