/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.voice

import android.os.Build
import android.view.View
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import com.manjul.aikeyboard.FlorisImeService
import com.manjul.aikeyboard.ime.voiceui.VoicePanel
import com.manjul.aikeyboard.ime.voiceui.VoiceUiState

/**
 * The voice panel in the smartbar. Returns false (and renders nothing) while voice typing is hidden, so the
 * smartbar shows its usual content. While the panel is open, back dismisses it (API 33+ predictive back).
 */
@Composable
fun RowScope.smartbarVoicePanel(): Boolean {
    val controller = FlorisImeService.voiceSessionOrNull()?.controller ?: return false
    val state by controller.ui.collectAsState()
    if (state == VoiceUiState.Hidden) return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        BackDismisses(LocalView.current, controller::dismiss)
    }
    VoicePanel(
        state = state,
        onStop = controller::toggle,
        onDismiss = controller::dismiss,
        onAllowMic = controller::requestPermission,
        onResume = controller::toggle,
        onDownloadSystemModel = if (controller.canDownloadSystemModel) controller::downloadSystemModel else null,
        modifier = Modifier.weight(1f),
    )
    return true
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun BackDismisses(view: View, onBack: () -> Unit) {
    val current by rememberUpdatedState(onBack)
    DisposableEffect(view) {
        val dispatcher = view.findOnBackInvokedDispatcher()
        val callback = OnBackInvokedCallback { current() }
        // Overlay priority: handled before the IME's own back (which hides the keyboard).
        dispatcher?.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback)
        onDispose { dispatcher?.unregisterOnBackInvokedCallback(callback) }
    }
}
