/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.ai

import android.os.Build
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import com.manjul.aikeyboard.FlorisImeService
import com.manjul.aikeyboard.fork.voice.BackDismisses
import com.manjul.aikeyboard.ime.aiui.AiSheet
import com.manjul.aikeyboard.ime.aiui.AiSheetState
import com.manjul.aikeyboard.ime.theme.FlorisImeUi
import org.florisboard.lib.snygg.ui.SnyggBox

/**
 * The ✦ sheet over the keyboard area (PRD FR-15). Renders nothing while the sheet is hidden. While shown it covers
 * the keys with the keyboard's window background and takes every touch, so a key press cannot reach the field
 * behind it; Back closes it first.
 */
@Composable
fun BoxScope.AiSheetOverlay() {
    val session = FlorisImeService.aiSessionOrNull() ?: return
    val state by session.rewrite.state.collectAsState()
    if (state == AiSheetState.Hidden) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        BackDismisses(LocalView.current) { session.rewrite.dismiss() }
    }
    SnyggBox(
        FlorisImeUi.Window.elementName,
        modifier = Modifier
            .matchParentSize()
            // Swallow touches that the sheet's own controls do not handle.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
    ) {
        AiSheet(
            controller = session.rewrite,
            onSeePro = session::seePro,
            nowMs = System::currentTimeMillis,
        )
    }
}
