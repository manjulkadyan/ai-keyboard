/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.voiceui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * A tappable pill in the voice panel ("Allow microphone…", "Tap to resume", download offers). With [maxLines] above
 * 1 the padding and line height shrink so two lines fit the smartbar height, and with large fonts the text shrinks
 * until it fits, down to [MinMultiLineTextSize] whatever the font scale; only below that is it ellipsized.
 */
@Composable
fun VoiceChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, maxLines: Int = 1) {
    val multiLine = maxLines > 1
    val color = LocalContentColor.current
    val autoSize = if (multiLine) {
        val max = LocalTextStyle.current.fontSize.takeIf { it.isSp } ?: 14.sp
        val min = with(LocalDensity.current) { MinMultiLineTextSize.toSp() }
        TextAutoSize.StepBased(minFontSize = min, maxFontSize = max, stepSize = 0.5.sp)
    } else {
        null
    }
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
            modifier = Modifier.padding(horizontal = 12.dp, vertical = if (multiLine) 2.dp else 6.dp),
            style = TextStyle(
                textDirection = TextDirection.Content,
                lineHeight = if (multiLine) 1.15.em else TextUnit.Unspecified,
            ),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            autoSize = autoSize,
        )
    }
}

/** Smallest text a two-line chip shrinks to, in dp so it does not grow with the font scale (device check 4.6). */
private val MinMultiLineTextSize = 12.dp
