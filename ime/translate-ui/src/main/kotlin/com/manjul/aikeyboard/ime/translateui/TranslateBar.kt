/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.translateui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The open Translate bar: pair chip with swap, undo chip and close on top; the buffer line; the preview line with
 * the in-flight indicator and ✓; the status chip when there is one. Colors come from [LocalContentColor] (the
 * smartbar theme), as in the voice panel. Preview changes are announced politely for TalkBack. Buffer and preview
 * are user content: never logged.
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
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The pair takes the room left of undo and close; its names ellipsize before the swap button shrinks.
            PairChip(state.pair, onSwap, Modifier.weight(1f))
            UndoChip(state.undoAvailable, onUndo, undoId = state.undoId)
            GlyphButton(stringResource(R.string.translate_bar__close_cd), onClose) { closeGlyph(it) }
        }
        BufferLine(state.input)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PreviewLine(state.preview, Modifier.weight(1f))
            if (state.inFlight) {
                val translating = stringResource(R.string.translate_bar__translating_cd)
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp).semantics { contentDescription = translating },
                    color = LocalContentColor.current,
                    strokeWidth = 2.dp,
                )
            }
            GlyphButton(
                label = stringResource(R.string.translate_bar__insert_cd),
                onClick = onInsert,
                enabled = !state.preview.isNullOrEmpty(),
            ) { checkGlyph(it) }
        }
        BarStatusChip(
            status = state.status,
            onDownload = onDownload,
            onOpenOfflineLanguages = onOpenOfflineLanguages,
            onRetry = onRetry,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}

/**
 * What the user typed into the bar. `TextDirection.Content` aligns Urdu or Arabic right whatever the layout
 * direction of the keyboard; the start is ellipsized so the end being typed stays visible.
 */
@Composable
private fun BufferLine(input: String) {
    val color = LocalContentColor.current
    val label = stringResource(R.string.translate_bar__buffer_cd)
    val empty = input.isEmpty()
    Text(
        text = if (empty) stringResource(R.string.translate_bar__buffer_hint) else input,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag(BUFFER_TAG)
            .semantics { contentDescription = label },
        color = if (empty) color.copy(alpha = 0.6f) else color,
        style = TextStyle(textDirection = TextDirection.Content, textAlign = TextAlign.Start),
        maxLines = 1,
        overflow = if (empty) TextOverflow.Ellipsis else TextOverflow.StartEllipsis,
    )
}

/** The latest good translation. A polite live region, so TalkBack reads a new preview after what it is saying. */
@Composable
private fun PreviewLine(preview: String?, modifier: Modifier) {
    val description = if (preview.isNullOrEmpty()) {
        stringResource(R.string.translate_bar__preview_cd)
    } else {
        stringResource(R.string.translate_bar__preview_announcement, preview)
    }
    Text(
        text = preview.orEmpty(),
        modifier = modifier
            .testTag(PREVIEW_TAG)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = description
            },
        style = TextStyle(textDirection = TextDirection.Content, textAlign = TextAlign.Start),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

internal const val BUFFER_TAG = "translate_bar_buffer"
internal const val PREVIEW_TAG = "translate_bar_preview"
