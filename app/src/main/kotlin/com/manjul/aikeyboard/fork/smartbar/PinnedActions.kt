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
 * the sticky action is always the mic, and the dynamic actions are the pinned chips, at most
 * [ChipArrangement.MAX_PINS]. The private-app action does not count as a pin and stays last, so it lands in the
 * overflow panel (otherwise "Make this app private" would be reachable only from the lock indicator).
 */
object PinnedActions {
    val Mic: QuickAction = QuickAction.InsertKey(TextKeyData.VOICE_INPUT)

    fun isMic(action: QuickAction): Boolean = action.keyData().code == KeyCode.VOICE_INPUT

    /** Dynamic actions that do not use up a pin. */
    fun isExempt(action: QuickAction): Boolean = action.keyData().code == KeyCode.TOGGLE_PRIVATE_APP

    /** Pins used by [dynamic]. The editor's placeholder and drag-marker tiles do not count. */
    fun pinnedCount(dynamic: List<QuickAction>): Int = dynamic.count { it.keyData().code !in NotCounted }

    /** False for the mic (never movable) and for a pin beyond [ChipArrangement.MAX_PINS]. */
    fun canPin(dynamic: List<QuickAction>, action: QuickAction): Boolean = when {
        isMic(action) -> false
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
     * [lockSticky], then keeps the first [ChipArrangement.MAX_PINS] pins and moves the rest to the front of the
     * hidden actions. A former sticky action other than the mic is hidden too. Idempotent.
     */
    fun normalize(arrangement: QuickActionArrangement): QuickActionArrangement {
        val formerSticky = arrangement.stickyAction?.takeUnless(::isMic)
        val locked = lockSticky(arrangement.distinct())
        val (exempt, counted) = locked.dynamicActions.partition(::isExempt)
        val pinned = counted.take(ChipArrangement.MAX_PINS)
        val overflow = counted.drop(ChipArrangement.MAX_PINS)
        return QuickActionArrangement(
            stickyAction = Mic,
            dynamicActions = pinned + exempt,
            hiddenActions = overflow + listOfNotNull(formerSticky) + locked.hiddenActions,
        ).distinct()
    }

    private val NotCounted = setOf(KeyCode.VOICE_INPUT, KeyCode.TOGGLE_PRIVATE_APP, KeyCode.NOOP, KeyCode.DRAG_MARKER)
}
