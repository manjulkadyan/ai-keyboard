/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.translateui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The open Translate bar: pair chip with swap, buffer line, preview line, status chip, ✓, undo chip and close.
 * Preview changes are announced politely for TalkBack. Buffer and preview are user content: never logged.
 *
 * Scaffold: signature only; written with `TranslateBarUiTest`.
 */
@Composable
fun TranslateBar(
    state: BarUiState.Open,
    onSwap: () -> Unit,
    onInsert: () -> Unit,
    onUndo: () -> Unit,
    onClose: () -> Unit,
    onDownload: () -> Unit,
    onOpenOfflineLanguages: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
}
