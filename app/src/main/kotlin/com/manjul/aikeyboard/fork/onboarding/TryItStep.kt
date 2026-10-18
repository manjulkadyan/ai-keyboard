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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import org.florisboard.lib.compose.stringRes

/**
 * O5: a text field to type in with the new keyboard, a microphone button and Skip. The microphone permission
 * is requested ([requestMicPermission]) only after the voice notice was agreed to; a denial ([micGranted] =
 * false) only disables voice, typing keeps working.
 */
@Composable
fun TryItStep(
    engineKind: () -> DisclosureEngineKind,
    store: FeatureSettingsStore,
    clock: () -> Long,
    micGranted: Boolean?,
    requestMicPermission: () -> Unit,
    onSkip: () -> Unit,
    onDone: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var showNotice by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text(stringRes(R.string.onboarding__try_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(stringRes(R.string.onboarding__try_body), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringRes(R.string.onboarding__try_field_hint)) },
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { showNotice = true }, enabled = micGranted != false) {
            Text(stringRes(R.string.onboarding__try_mic))
        }
        if (micGranted == false) {
            Text(stringRes(R.string.onboarding__try_mic_denied), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onSkip) { Text(stringRes(R.string.onboarding__skip)) }
            Spacer(Modifier.weight(1f))
            Button(onClick = onDone) { Text(stringRes(R.string.onboarding__done)) }
        }
    }

    if (showNotice) {
        VoiceDisclosureSheet(
            engineKind = engineKind,
            store = store,
            clock = clock,
            onAgree = {
                showNotice = false
                requestMicPermission()
            },
            onDismiss = { showNotice = false },
        )
    }
}
