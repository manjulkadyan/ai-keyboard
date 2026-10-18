/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.voiceui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** A tappable pill in the voice panel ("Allow microphone…", "Tap to resume", download offers). */
@Composable
fun VoiceChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        contentColor = color,
        border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = TextStyle(textDirection = TextDirection.Content),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
