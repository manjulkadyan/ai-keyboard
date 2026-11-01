/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.voiceui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay

/** Recognizer RMS (dB, noise floor near 0, speech up to about 10) as a 0..1 bar height. */
internal fun rmsToLevel(rmsDb: Float): Float = ((rmsDb - MIN_DB) / (MAX_DB - MIN_DB)).coerceIn(0f, 1f)

private const val MIN_DB = -2f
private const val MAX_DB = 10f
private const val BARS = 24
private const val TICK_MS = 50L

/** Weight of the newest reading in the input low-pass (EMA), so one spiky RMS callback does not show as a tall bar. */
internal const val INPUT_ALPHA = 0.6f

/** Share of the gap closed per tick when the level rises: a loud word reaches about 80% in 3 ticks (150 ms). */
internal const val RISE = 0.6f

/** Share of the gap closed per tick when the level falls: slower than the rise, so silence fades out. */
internal const val FALL = 0.25f

/**
 * Smooths 0..1 levels for the bars, one [step] per clock tick: an EMA on the input, then an eased rise and a
 * slower fall. Neighbouring bars change by at most [RISE] of the gap, so they never jump to very different heights.
 */
internal class WaveSmoother {
    private var filtered = 0f
    private var shown = 0f

    fun step(input: Float): Float {
        filtered += (input - filtered) * INPUT_ALPHA
        val gap = filtered - shown
        shown += gap * (if (gap > 0f) RISE else FALL)
        return shown
    }
}

/**
 * Scrolling bars, newest on the end side. The bars move on a fixed [TICK_MS] clock and sample the latest RMS, so
 * the speed does not depend on how often (or with which values) the recognizer reports RMS.
 */
@Composable
fun Waveform(rmsDb: Float, color: Color, modifier: Modifier = Modifier) {
    val levels = remember { mutableStateListOf<Float>().apply { repeat(BARS) { add(0f) } } }
    val latest by rememberUpdatedState(rmsDb)
    LaunchedEffect(Unit) {
        val smoother = WaveSmoother()
        while (true) {
            delay(TICK_MS)
            levels.removeAt(0)
            levels.add(smoother.step(rmsToLevel(latest)))
        }
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
