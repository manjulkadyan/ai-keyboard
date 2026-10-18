/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.smartbar

import com.manjul.aikeyboard.ime.policy.PolicyDecision

object ChipArrangement {
    const val MAX_PINS = 3

    /** Appends [chip] to the pins. Fixed chips, duplicates and a 4th pin leave [current] unchanged. */
    fun pin(current: List<ChipId>, chip: ChipId): List<ChipId> =
        if (chip in ChipId.fixed || chip in current || current.size >= MAX_PINS) current else current + chip

    /**
     * Moves the chip at [from] to [to] among the movable chips. Fixed chips keep their positions; a move
     * that starts or ends on a fixed chip, or outside the list, changes nothing.
     */
    fun reorder(order: List<ChipId>, from: Int, to: Int): List<ChipId> {
        if (from !in order.indices || to !in order.indices || from == to) return order
        if (order[from] in ChipId.fixed || order[to] in ChipId.fixed) return order
        val slots = order.indices.filter { order[it] !in ChipId.fixed }
        val movable = slots.map { order[it] }.toMutableList()
        movable.add(slots.indexOf(to), movable.removeAt(slots.indexOf(from)))
        val result = order.toMutableList()
        slots.forEachIndexed { i, slot -> result[slot] = movable[i] }
        return result
    }

    /** The chips to render, in [order]. Chips the [decision] disallows are removed, not disabled. */
    fun visible(
        order: List<ChipId>,
        registered: Set<ChipId>,
        decision: PolicyDecision,
        clipboardHistoryOn: Boolean,
    ): List<ChipId> = order.filter { it in registered && allowed(it, decision, clipboardHistoryOn) }

    fun allowed(chip: ChipId, decision: PolicyDecision, clipboardHistoryOn: Boolean): Boolean = when (chip) {
        ChipId.MIC -> decision.allowVoice
        ChipId.TRANSLATE -> decision.allowTranslate
        ChipId.AI -> decision.allowAi
        ChipId.CLIPBOARD -> decision.allowClipboardHistory && clipboardHistoryOn
        ChipId.STICKERS -> decision.allowCloud
        ChipId.EMOJI, ChipId.SETTINGS -> true
    }
}
