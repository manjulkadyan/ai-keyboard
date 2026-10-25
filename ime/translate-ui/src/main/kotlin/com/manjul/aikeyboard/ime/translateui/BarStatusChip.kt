/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.translateui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.manjul.aikeyboard.core.translate.bar.BarStatus

/**
 * The inline status chip for [status]: "Download …" calls [onDownload], "Offline: download languages" calls
 * [onOpenOfflineLanguages], "Retry" calls [onRetry], "Needs internet (Pro)" is informational. Nothing for
 * `Idle`/`Ok`.
 *
 * Scaffold: signature only.
 */
@Composable
fun BarStatusChip(
    status: BarStatus,
    onDownload: () -> Unit,
    onOpenOfflineLanguages: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
}
