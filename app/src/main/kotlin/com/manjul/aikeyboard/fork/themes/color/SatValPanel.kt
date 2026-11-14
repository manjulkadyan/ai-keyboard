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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.theme.Hsv
import org.florisboard.lib.compose.stringRes
import kotlin.math.roundToInt

/**
 * The saturation and value square of the colour picker: saturation grows to the right, value to the top. A tap or a
 * drag sets both. The caller sizes it to at least 48 dp.
 */
@Composable
internal fun SatValPanel(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (saturation: Float, value: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val change by rememberUpdatedState(onChange)
    val description = stringRes(R.string.color_picker__sat_val)
    val state = stringRes(
        R.string.color_picker__sat_val_state,
        "s" to (saturation * 100).roundToInt(),
        "v" to (value * 100).roundToInt(),
    )
    val pure = Color(Hsv(hue, 1f, 1f).toArgb().toInt())
    Canvas(
        modifier = modifier
            .semantics {
                contentDescription = description
                stateDescription = state
            }
            .pointerInput(Unit) {
                fun report(p: Offset) {
                    val s = (p.x / size.width).coerceIn(0f, 1f)
                    val v = 1f - (p.y / size.height).coerceIn(0f, 1f)
                    change(s, v)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    report(down.position)
                    drag(down.id) { move ->
                        move.consume()
                        report(move.position)
                    }
                }
            },
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, pure)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val thumb = Offset(saturation * size.width, (1f - value) * size.height)
        drawCircle(Color.White, radius = 9.dp.toPx(), center = thumb, style = Stroke(3.dp.toPx()))
        drawCircle(Color.Black, radius = 11.dp.toPx(), center = thumb, style = Stroke(1.dp.toPx()))
    }
}
