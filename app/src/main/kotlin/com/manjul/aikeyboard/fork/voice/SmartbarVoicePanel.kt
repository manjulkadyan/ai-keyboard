/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.voice

import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.net.toUri
import com.manjul.aikeyboard.FlorisImeService
import com.manjul.aikeyboard.app.FlorisAppActivity
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
    val context = LocalContext.current
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
        onDownloadWhisperModel = {
            controller.dismiss()
            context.startActivity(voiceSettingsIntent(context))
        },
        modifier = Modifier.weight(1f),
    )
    return true
}

/** Opens Voice settings (Routes.Settings.Voice, deep link `settings/voice`), where offline models are downloaded. */
internal fun voiceSettingsIntent(context: Context): Intent =
    Intent(Intent.ACTION_VIEW, VOICE_SETTINGS_DEEPLINK.toUri(), context, FlorisAppActivity::class.java)
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                Intent.FLAG_ACTIVITY_CLEAR_TOP,
        )

private const val VOICE_SETTINGS_DEEPLINK = "ui://florisboard/settings/voice"

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun BackDismisses(view: View, onBack: () -> Unit) {
    val current by rememberUpdatedState(onBack)
    DisposableEffect(view) {
        val dispatcher = view.findOnBackInvokedDispatcher()
        val callback = OnBackInvokedCallback { current() }
        // Overlay priority: handled before the IME's own back (which hides the keyboard).
        dispatcher?.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback)
        onDispose { dispatcher?.unregisterOnBackInvokedCallback(callback) }
    }
}
