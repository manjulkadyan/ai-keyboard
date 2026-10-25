/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.translateui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.manjul.aikeyboard.core.translate.Lang
import java.util.Locale

// Shared look of the bar, matching the voice panel in :ime:voice-ui: colors from LocalContentColor (the smartbar
// theme), outlined pills, IconButtons (48 dp touch targets) with glyphs drawn on a Canvas (no icon dependency).

/** The language's name in the UI language, from the system (`Locale.getDisplayName`), never from our strings. */
@Composable
@ReadOnlyComposable
internal fun Lang.displayName(): String {
    val ui = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    return Locale.forLanguageTag(tag).getDisplayName(ui).ifBlank { tag }
}

/**
 * An outlined pill like `VoiceChip`. With [onClick] it is a button whose touch target Material3 grows to 48 dp;
 * without, it is informational text.
 */
@Composable
internal fun BarChip(
    label: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val color = LocalContentColor.current
    val shape = RoundedCornerShape(50)
    val border = BorderStroke(1.dp, color.copy(alpha = if (onClick != null) 0.6f else 0.3f))
    val semantics = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    val content: @Composable () -> Unit = {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = TextStyle(textDirection = TextDirection.Content),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.then(semantics),
            shape = shape,
            color = Color.Transparent,
            contentColor = color,
            border = border,
            content = content,
        )
    } else {
        Surface(
            modifier = modifier.then(semantics),
            shape = shape,
            color = Color.Transparent,
            contentColor = color,
            border = border,
            content = content,
        )
    }
}

/** A 48 dp IconButton whose glyph is drawn by [glyph] in [LocalContentColor], labelled [label] for TalkBack. */
@Composable
internal fun GlyphButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    glyph: DrawScope.(Color) -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics { contentDescription = label },
    ) {
        val color = LocalContentColor.current
        Canvas(Modifier.size(16.dp)) { glyph(color) }
    }
}

internal fun DrawScope.closeGlyph(color: Color) {
    val stroke = 2.dp.toPx()
    val inset = size.minDimension * 0.125f
    drawLine(color, Offset(inset, inset), Offset(size.width - inset, size.height - inset), stroke, StrokeCap.Round)
    drawLine(color, Offset(size.width - inset, inset), Offset(inset, size.height - inset), stroke, StrokeCap.Round)
}

internal fun DrawScope.checkGlyph(color: Color) {
    val stroke = 2.dp.toPx()
    val w = size.width
    val h = size.height
    drawLine(color, Offset(w * 0.1f, h * 0.55f), Offset(w * 0.4f, h * 0.85f), stroke, StrokeCap.Round)
    drawLine(color, Offset(w * 0.4f, h * 0.85f), Offset(w * 0.9f, h * 0.2f), stroke, StrokeCap.Round)
}

/** Two opposite arrows (⇄). Symmetric, so it needs no mirroring in RTL. */
internal fun DrawScope.swapGlyph(color: Color) {
    val stroke = 2.dp.toPx()
    val w = size.width
    val h = size.height
    val head = w * 0.22f
    // Upper arrow pointing right.
    drawLine(color, Offset(w * 0.1f, h * 0.3f), Offset(w * 0.9f, h * 0.3f), stroke, StrokeCap.Round)
    drawLine(color, Offset(w * 0.9f, h * 0.3f), Offset(w * 0.9f - head, h * 0.3f - head), stroke, StrokeCap.Round)
    drawLine(color, Offset(w * 0.9f, h * 0.3f), Offset(w * 0.9f - head, h * 0.3f + head), stroke, StrokeCap.Round)
    // Lower arrow pointing left.
    drawLine(color, Offset(w * 0.9f, h * 0.7f), Offset(w * 0.1f, h * 0.7f), stroke, StrokeCap.Round)
    drawLine(color, Offset(w * 0.1f, h * 0.7f), Offset(w * 0.1f + head, h * 0.7f - head), stroke, StrokeCap.Round)
    drawLine(color, Offset(w * 0.1f, h * 0.7f), Offset(w * 0.1f + head, h * 0.7f + head), stroke, StrokeCap.Round)
}

/** An arrow pointing in reading direction: right in LTR, left in RTL. */
internal fun DrawScope.forwardArrowGlyph(color: Color, rtl: Boolean) {
    val stroke = 1.5.dp.toPx()
    val w = size.width
    val h = size.height
    val (from, to) = if (rtl) w * 0.9f to w * 0.1f else w * 0.1f to w * 0.9f
    val head = (if (rtl) 1 else -1) * w * 0.3f
    drawLine(color, Offset(from, h / 2), Offset(to, h / 2), stroke, StrokeCap.Round)
    drawLine(color, Offset(to, h / 2), Offset(to + head, h / 2 - w * 0.3f), stroke, StrokeCap.Round)
    drawLine(color, Offset(to, h / 2), Offset(to + head, h / 2 + w * 0.3f), stroke, StrokeCap.Round)
}
