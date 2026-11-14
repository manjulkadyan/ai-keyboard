/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.themes.color

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.theme.Hsv
import org.florisboard.lib.compose.stringRes

/**
 * The HSV colour picker (S5 Task 8, PRD FR-18): a hue ring around a saturation and value square, a hex field and the
 * recent colours. Every change goes to [onColorChange]. The caller adds a colour to the recents when the user is done.
 * The eyedropper button shows only when [eyedropper] is given; S5 passes null (decision 16).
 */
@Composable
fun HsvColorPicker(
    color: Long,
    recents: List<Long>,
    onColorChange: (Long) -> Unit,
    eyedropper: (() -> Unit)? = null,
) {
    // The HSV is kept here, so a grey or black keeps the hue the user chose instead of snapping to red.
    var hsv by remember { mutableStateOf(Hsv.fromArgb(color)) }
    LaunchedEffect(color) {
        if (hsv.toArgb() != color) hsv = Hsv.fromArgb(color)
    }
    fun set(next: Hsv) {
        hsv = next
        onColorChange(next.toArgb())
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(RING_SIZE), contentAlignment = Alignment.Center) {
            HueRing(hue = hsv.h, onHueChange = { set(hsv.copy(h = it)) }, modifier = Modifier.fillMaxSize())
            SatValPanel(
                hue = hsv.h,
                saturation = hsv.s,
                value = hsv.v,
                onChange = { s, v -> set(hsv.copy(s = s, v = v)) },
                modifier = Modifier.size(PANEL_SIZE),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            HexField(color = color, onColorChange = onColorChange, modifier = Modifier.weight(1f))
            if (eyedropper != null) {
                IconButton(onClick = eyedropper) {
                    Icon(Icons.Default.Colorize, contentDescription = stringRes(R.string.color_picker__eyedropper))
                }
            }
        }
        if (recents.isNotEmpty()) RecentColorsRow(recents = recents, onPick = onColorChange)
    }
}

private val RING_SIZE = 260.dp

/** Fits inside the ring's touch band: the diagonal stays under the inner diameter minus the band. */
private val PANEL_SIZE = 120.dp
