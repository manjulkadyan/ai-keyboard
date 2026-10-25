/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.editing.testing

/**
 * Asserts that the IME side never deleted or changed a host character (PRD FR-13 AC1, Review Focus 1). Characters are
 * tracked by identity, not position or value: the IME writing an equal character over a host one is still a change.
 * Characters the host removed itself are not counted. The field text is test data, but violations name positions only.
 */
class HostSafetyChecker(private val editor: FakeEditor) {
    /** Host characters in the field when the checker was created (before the bar or the dictation opened). */
    private val snapshot: List<FakeEditor.Cell> = editor.hostCells

    /**
     * Host characters that should be in the field but are not: those of the [snapshot] and those the host typed later,
     * unless the host removed them itself. Empty when the field is safe.
     */
    fun violations(): List<String> {
        val owned = editor.hostOwned
        return owned.withIndex().filter { (_, cell) -> !editor.contains(cell) }.map { (index, cell) ->
            val origin = if (snapshot.any { it === cell }) "present at start" else "typed later"
            "host char #$index ($origin) removed or replaced by the IME side"
        }
    }

    fun assertSafe() {
        val violations = violations()
        if (violations.isNotEmpty()) throw AssertionError(violations.joinToString("\n"))
    }
}
