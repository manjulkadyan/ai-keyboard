/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.themes.color

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.theme.Hsv
import org.florisboard.lib.compose.stringRes
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** The ring's visible width. */
internal val HUE_RING_WIDTH: Dp = 36.dp

/** The ring's touch band, at least 48 dp: it reaches this far inside the visible ring. */
private val TOUCH_SLACK: Dp = 12.dp

private val HUES = List(7) { Color(Hsv(it * 60f, 1f, 1f).toArgb().toInt()) }

/**
 * The hue ring of the colour picker. Hue 0 (red) is at 3 o'clock and grows clockwise. A tap or a drag on the ring
 * sets the hue. Accessibility services adjust it as a 0..360 range.
 */
@Composable
internal fun HueRing(hue: Float, onHueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val onChange by rememberUpdatedState(onHueChange)
    val description = stringRes(R.string.color_picker__hue)
    Canvas(
        modifier = modifier
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(hue, 0f..360f)
                setProgress { value -> onChange(value.coerceIn(0f, 360f) % 360f); true }
            }
            .pointerInput(Unit) {
                val ringWidth = HUE_RING_WIDTH.toPx()
                val slack = TOUCH_SLACK.toPx()
                fun hueAt(p: Offset): Float {
                    val deg = Math.toDegrees(atan2(p.y - size.height / 2f, p.x - size.width / 2f).toDouble()).toFloat()
                    return (deg + 360f) % 360f
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val outer = min(size.width, size.height) / 2f
                    val distance = hypot(down.position.x - size.width / 2f, down.position.y - size.height / 2f)
                    if (distance < outer - ringWidth - slack) return@awaitEachGesture
                    down.consume()
                    onChange(hueAt(down.position))
                    drag(down.id) { change ->
                        change.consume()
                        onChange(hueAt(change.position))
                    }
                }
            },
    ) {
        val ringWidth = HUE_RING_WIDTH.toPx()
        val radius = size.minDimension / 2f - ringWidth / 2f
        drawCircle(brush = Brush.sweepGradient(HUES, center), radius = radius, style = Stroke(ringWidth))
        val angle = Math.toRadians(hue.toDouble())
        val thumb = Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
        drawCircle(Color.White, radius = ringWidth / 2f - 2.dp.toPx(), center = thumb, style = Stroke(3.dp.toPx()))
        drawCircle(Color.Black, radius = ringWidth / 2f, center = thumb, style = Stroke(1.dp.toPx()))
    }
}
