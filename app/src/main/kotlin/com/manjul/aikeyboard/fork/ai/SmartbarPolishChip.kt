/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.ai

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.manjul.aikeyboard.FlorisImeService
import com.manjul.aikeyboard.ime.aiui.PolishChip
import com.manjul.aikeyboard.ime.aiui.PolishUndoChip

/**
 * After a dictation, the ✦ Polish chips ("Clean up", "Clean up + translate") replace the rest of the smartbar main
 * row for up to 30 s (Sprint 3 Task 14). After an auto-polish, an "Undo" chip takes the row for the undo window
 * instead of a sheet over the keys. Returns false, rendering nothing, while neither is shown.
 */
@Composable
fun RowScope.smartbarPolishChip(): Boolean {
    val session = FlorisImeService.aiSessionOrNull() ?: return false
    val chip by session.polish.chip.collectAsState()
    val autoUndo by session.polish.autoUndo.collectAsState()
    if (autoUndo) {
        PolishUndoChip(
            onUndo = { session.polish.undo() },
            modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 8.dp),
        )
        return true
    }
    val state = chip ?: return false
    PolishChip(
        state = state,
        onCleanUp = { session.polish(translate = false) },
        onCleanUpAndTranslate = { session.polish(translate = true) },
        modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 8.dp),
    )
    return true
}
