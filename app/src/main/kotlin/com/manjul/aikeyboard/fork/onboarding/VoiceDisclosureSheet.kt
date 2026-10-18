/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

/**
 * Voice notice shown before the first microphone request. It names the recognizer that will be used
 * ([engineKind] is read when the sheet is shown) and records `consentVoiceNoticeAt` on "Agree".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceDisclosureSheet(
    engineKind: () -> DisclosureEngineKind,
    store: FeatureSettingsStore,
    clock: () -> Long,
    onAgree: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val body = when (engineKind()) {
        DisclosureEngineKind.ON_DEVICE -> R.string.onboarding__voice_notice_on_device
        DisclosureEngineKind.SYSTEM_SERVICE -> R.string.onboarding__voice_notice_system
        DisclosureEngineKind.WHISPER -> R.string.onboarding__voice_notice_whisper
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(stringRes(R.string.onboarding__voice_notice_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Text(stringRes(body), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text(stringRes(R.string.onboarding__voice_notice_not_now)) }
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    scope.launch { store.update { it.copy(consentVoiceNoticeAt = clock()) } }
                    onAgree()
                }) { Text(stringRes(R.string.onboarding__voice_notice_agree)) }
            }
        }
    }
}
