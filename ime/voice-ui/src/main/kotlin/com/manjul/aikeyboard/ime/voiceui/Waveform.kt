/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.voiceui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/** Recognizer RMS (dB, noise floor near 0, speech up to about 10) as a 0..1 bar height. */
internal fun rmsToLevel(rmsDb: Float): Float = ((rmsDb - MIN_DB) / (MAX_DB - MIN_DB)).coerceIn(0f, 1f)

private const val MIN_DB = -2f
private const val MAX_DB = 10f
private const val BARS = 24

/** Scrolling bars of the last [BARS] RMS readings, newest on the end side. */
@Composable
fun Waveform(rmsDb: Float, color: Color, modifier: Modifier = Modifier) {
    val levels = remember { mutableStateListOf<Float>().apply { repeat(BARS) { add(0f) } } }
    LaunchedEffect(rmsDb) {
        levels.removeAt(0)
        levels.add(rmsToLevel(rmsDb))
    }
    Canvas(modifier) {
        val slot = size.width / BARS
        val barWidth = slot * 0.6f
        val minHeight = size.height * 0.08f
        levels.forEachIndexed { i, level ->
            val h = minHeight + (size.height - minHeight) * level
            drawRoundRect(
                color = color,
                topLeft = Offset(i * slot + (slot - barWidth) / 2, (size.height - h) / 2),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
    }
}
