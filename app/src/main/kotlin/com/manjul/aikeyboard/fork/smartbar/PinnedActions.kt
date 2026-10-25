/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.smartbar

import com.manjul.aikeyboard.ime.smartbar.quickaction.QuickAction
import com.manjul.aikeyboard.ime.smartbar.quickaction.QuickActionArrangement
import com.manjul.aikeyboard.ime.smartbar.quickaction.keyData
import com.manjul.aikeyboard.ime.text.key.KeyCode
import com.manjul.aikeyboard.ime.text.keyboard.TextKeyData

/**
 * Pin rules (PRD FR-07) on upstream's `prefs.smartbar.actionArrangement`, the only store of the smartbar layout:
 * the sticky action is always the mic, then the fixed Translate action (first dynamic action, never moved, hidden
 * or counted as a pin), then the pinned chips, at most [ChipArrangement.MAX_PINS]. The private-app action does not count as a pin and stays last, so it lands in the
 * overflow panel (otherwise "Make this app private" would be reachable only from the lock indicator).
 */
object PinnedActions {
    val Mic: QuickAction = QuickAction.InsertKey(TextKeyData.VOICE_INPUT)

    fun isMic(action: QuickAction): Boolean = action.keyData().code == KeyCode.VOICE_INPUT

    /** The fixed Translate action (S2-bar): always the first dynamic action. */
    val Translate: QuickAction = QuickAction.InsertKey(TextKeyData.TOGGLE_TRANSLATE_BAR)

    fun isTranslate(action: QuickAction): Boolean = action.keyData().code == KeyCode.TOGGLE_TRANSLATE_BAR

    /** Neither the mic nor Translate can be moved by the user. */
    fun isFixed(action: QuickAction): Boolean = isMic(action) || isTranslate(action)

    /** Dynamic actions that do not use up a pin. */
    fun isExempt(action: QuickAction): Boolean = action.keyData().code == KeyCode.TOGGLE_PRIVATE_APP

    /** Pins used by [dynamic]. The editor's placeholder and drag-marker tiles do not count. */
    fun pinnedCount(dynamic: List<QuickAction>): Int = dynamic.count { it.keyData().code !in NotCounted }

    /** False for the mic and Translate (never movable) and for a pin beyond [ChipArrangement.MAX_PINS]. */
    fun canPin(dynamic: List<QuickAction>, action: QuickAction): Boolean = when {
        isFixed(action) -> false
        isExempt(action) -> true
        else -> pinnedCount(dynamic) < ChipArrangement.MAX_PINS
    }

    /** The mic is the sticky action and nothing else is; it is removed from the dynamic and hidden actions. */
    fun lockSticky(arrangement: QuickActionArrangement): QuickActionArrangement = QuickActionArrangement(
        stickyAction = Mic,
        dynamicActions = arrangement.dynamicActions.filterNot(::isMic),
        hiddenActions = arrangement.hiddenActions.filterNot(::isMic),
    )

    /**
     * [lockSticky], puts Translate first among the dynamic actions, then keeps the first [ChipArrangement.MAX_PINS]
     * pins and moves the rest to the front of the hidden actions. A former sticky action other than the mic is hidden
     * too (Translate is never hidden). Idempotent.
     */
    fun normalize(arrangement: QuickActionArrangement): QuickActionArrangement {
        val formerSticky = arrangement.stickyAction?.takeUnless(::isFixed)
        val locked = lockSticky(arrangement.distinct())
        val (exempt, counted) = locked.dynamicActions.filterNot(::isTranslate).partition(::isExempt)
        val pinned = counted.take(ChipArrangement.MAX_PINS)
        val overflow = counted.drop(ChipArrangement.MAX_PINS)
        return QuickActionArrangement(
            stickyAction = Mic,
            dynamicActions = listOf(Translate) + pinned + exempt,
            hiddenActions = overflow + listOfNotNull(formerSticky) + locked.hiddenActions.filterNot(::isTranslate),
        ).distinct()
    }

    private val NotCounted = setOf(
        KeyCode.VOICE_INPUT,
        KeyCode.TOGGLE_TRANSLATE_BAR,
        KeyCode.TOGGLE_PRIVATE_APP,
        KeyCode.NOOP,
        KeyCode.DRAG_MARKER,
    )
}
