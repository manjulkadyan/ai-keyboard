/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.translateui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * "Undo" after an insert, shown while [visible] and for at most [windowMs].
 *
 * Scaffold: signature only.
 */
@Composable
fun UndoChip(
    visible: Boolean,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
    windowMs: Long = UNDO_WINDOW_MS,
) {
}

/** Same window as `OwnWriteUndo`'s default. */
const val UNDO_WINDOW_MS = 5_000L
