/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.smartbar

import com.manjul.aikeyboard.ime.text.key.KeyCode

/** Fork smartbar chips (PRD FR-20). Persisted by [name] in `FeatureSettings.smartbarPinned/Order`. */
enum class ChipId {
    MIC, TRANSLATE, AI, EMOJI, CLIPBOARD, STICKERS, SETTINGS;

    /** The upstream quick-action key this chip is wired to; null while the chip has no handler. */
    val keyCode: Int?
        get() = when (this) {
            MIC -> KeyCode.VOICE_INPUT // Task 11 replaces the handler
            EMOJI -> KeyCode.IME_UI_MODE_MEDIA
            CLIPBOARD -> KeyCode.IME_UI_MODE_CLIPBOARD
            SETTINGS -> KeyCode.SETTINGS
            TRANSLATE, AI, STICKERS -> null
        }

    companion object {
        /** Chips that can be neither pinned nor moved. */
        val fixed: Set<ChipId> = setOf(MIC, TRANSLATE)

        /** Chips with a handler this sprint. Translate and AI stay hidden; upstream has no stickers. */
        val registered: Set<ChipId> = setOf(MIC, EMOJI, CLIPBOARD, SETTINGS)

        /** Parses persisted names, dropping unknown ones (e.g. written by a newer version). */
        fun parse(names: List<String>): List<ChipId> = names.mapNotNull { n -> entries.firstOrNull { it.name == n } }

        fun forKeyCode(code: Int): ChipId? = entries.firstOrNull { it.keyCode == code }
    }
}
