/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.smartbar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.manjul.aikeyboard.FlorisImeService
import com.manjul.aikeyboard.R

/**
 * While "App made private" is offered with Undo, the message replaces the rest of the smartbar main row (the lock
 * indicator stays at its start). Returns false, rendering nothing, otherwise.
 */
@Composable
fun RowScope.smartbarPrivateAppUndo(): Boolean {
    val undo = FlorisImeService.privateAppUndoOrNull() ?: return false
    val visible by undo.visible.collectAsState()
    if (!visible) return false
    PrivateAppUndoMessage(onUndo = undo::undo, modifier = Modifier.weight(1f).fillMaxHeight())
    return true
}

/**
 * "App made private" and an Undo pill in the smartbar's colors. The message is a polite live region, so TalkBack
 * announces it after what it is saying. Undo has a 48 dp minimum target (Compose extends the touch area to 48 dp
 * where the smartbar row is lower).
 */
@Composable
fun PrivateAppUndoMessage(onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val message = stringResource(R.string.private_app_undo__message)
    val undoDescription = stringResource(R.string.private_app_undo__action_description)
    val color = LocalContentColor.current
    Row(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            modifier = Modifier
                .weight(1f)
                .testTag(PRIVATE_APP_UNDO_MESSAGE_TAG)
                .semantics { liveRegion = LiveRegionMode.Polite },
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Surface(
            onClick = onUndo,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .semantics { contentDescription = undoDescription },
            shape = RoundedCornerShape(50),
            color = Color.Transparent,
            contentColor = color,
            border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.private_app_undo__action),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    maxLines = 1,
                )
            }
        }
    }
}

const val PRIVATE_APP_UNDO_MESSAGE_TAG = "private_app_undo_message"
