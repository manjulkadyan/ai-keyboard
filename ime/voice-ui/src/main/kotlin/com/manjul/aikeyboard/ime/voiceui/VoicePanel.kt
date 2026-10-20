/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.voiceui

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Smartbar content while voice typing is not [VoiceUiState.Hidden]. Colors come from [LocalContentColor] (the
 * smartbar theme). Transcript text is never shown here: it goes to the host field as a composing region.
 */
@Composable
fun VoicePanel(
    state: VoiceUiState,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
    onAllowMic: () -> Unit,
    onResume: () -> Unit,
    onDownloadSystemModel: (() -> Unit)?,
    onDownloadWhisperModel: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val color = LocalContentColor.current
    Row(
        modifier = modifier.fillMaxHeight().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (state) {
            VoiceUiState.Hidden -> Unit
            is VoiceUiState.Listening -> {
                Waveform(state.rms, color, Modifier.width(72.dp).height(24.dp))
                Status(R.string.voice__listening)
                StopButton(onStop)
            }
            VoiceUiState.Transcribing -> {
                Status(R.string.voice__transcribing)
                StopButton(onStop)
            }
            VoiceUiState.NeedsPermission -> {
                VoiceChip(stringResource(R.string.voice__allow_mic), onAllowMic, Modifier.weight(1f, fill = false))
                Spacer(Modifier.weight(1f))
                CloseButton(onDismiss)
            }
            VoiceUiState.TapToResume -> {
                VoiceChip(stringResource(R.string.voice__tap_to_resume), onResume, Modifier.weight(1f, fill = false))
                Spacer(Modifier.weight(1f))
                CloseButton(onDismiss)
            }
            is VoiceUiState.Message -> {
                Status(state.kind.text())
                if (state.offerSystemDownload && onDownloadSystemModel != null) {
                    VoiceChip(stringResource(R.string.voice__download_system), onDownloadSystemModel)
                }
                if (state.offerWhisperDownload && onDownloadWhisperModel != null) {
                    VoiceChip(stringResource(R.string.voice__download_whisper), onDownloadWhisperModel)
                }
                CloseButton(onDismiss)
            }
        }
    }
}

@StringRes
private fun MessageKind.text(): Int = when (this) {
    MessageKind.NO_SERVICE -> R.string.voice__msg_no_service
    MessageKind.LANG_UNSUPPORTED -> R.string.voice__msg_lang_unsupported
    MessageKind.OFFLINE_NO_MODEL -> R.string.voice__msg_offline_no_model
    MessageKind.NO_LOCAL_ENGINE -> R.string.voice__msg_no_local_engine
    MessageKind.NETWORK -> R.string.voice__msg_network
    MessageKind.MIC_BUSY -> R.string.voice__msg_mic_busy
    MessageKind.NO_PERMISSION -> R.string.voice__msg_no_permission
    MessageKind.ERROR -> R.string.voice__msg_error
}

@Composable
private fun RowScope.Status(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        modifier = Modifier.weight(1f),
        // Messages follow the script of the UI language, not the layout direction of the host.
        style = TextStyle(textDirection = TextDirection.Content),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun StopButton(onClick: () -> Unit) {
    val color = LocalContentColor.current
    val label = stringResource(R.string.voice__stop)
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        Canvas(Modifier.size(14.dp)) {
            drawRoundRect(color = color, cornerRadius = CornerRadius(size.minDimension / 6))
        }
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit) {
    val color = LocalContentColor.current
    val label = stringResource(R.string.voice__close)
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        Canvas(Modifier.size(12.dp)) {
            val stroke = 2.dp.toPx()
            drawLine(color, Offset.Zero, Offset(size.width, size.height), stroke)
            drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), stroke)
        }
    }
}
