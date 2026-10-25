/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translate

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import com.manjul.aikeyboard.FlorisImeService
import com.manjul.aikeyboard.fork.voice.BackDismisses
import com.manjul.aikeyboard.ime.theme.FlorisImeUi
import com.manjul.aikeyboard.ime.translateui.BarUiState
import com.manjul.aikeyboard.ime.translateui.TranslateBar
import org.florisboard.lib.snygg.ui.SnyggColumn

/**
 * The open Translate bar, above the smartbar row and the keys, in the smartbar's theme. Renders nothing while the
 * bar is hidden or before the user first opened it. Back closes the bar (API 33+ predictive back; older versions
 * go through `BarKeyRouter.onHardwareKey`).
 */
@Composable
fun SmartbarTranslateBar() {
    val session = FlorisImeService.translateSessionOrNull() ?: return
    val controller by session.controllerOrNull.collectAsState()
    val c = controller ?: return
    val state by c.ui.collectAsState()
    val open = state as? BarUiState.Open ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        BackDismisses(LocalView.current, session::close)
    }
    SnyggColumn(FlorisImeUi.Smartbar.elementName) {
        TranslateBar(
            state = open,
            onSwap = c::swapPair,
            onInsert = session::insert,
            onUndo = c::undo,
            onClose = session::close,
            onDownload = c::download,
            onOpenOfflineLanguages = c::openOfflineLanguages,
            onRetry = c::retry,
        )
    }
}
