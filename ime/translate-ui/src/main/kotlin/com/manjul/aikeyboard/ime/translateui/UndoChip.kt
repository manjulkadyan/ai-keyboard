/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.translateui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay

/**
 * "Undo" after an insert, shown while [visible] and for at most [windowMs] after [visible] became true or [undoId]
 * changed (a new insert); then it hides itself even if [visible] stays true (the controller's own undo window ends
 * at the same time).
 */
@Composable
fun UndoChip(
    visible: Boolean,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
    undoId: Long = 0,
    windowMs: Long = UNDO_WINDOW_MS,
) {
    var expired by remember(visible, undoId) { mutableStateOf(false) }
    LaunchedEffect(visible, undoId, windowMs) {
        if (visible) {
            delay(windowMs)
            expired = true
        }
    }
    if (visible && !expired) {
        BarChip(
            label = stringResource(R.string.translate_bar__undo),
            onClick = onUndo,
            modifier = modifier,
            contentDescription = stringResource(R.string.translate_bar__undo_cd),
        )
    }
}

/** Same window as `OwnWriteUndo`'s default. */
const val UNDO_WINDOW_MS = 5_000L
