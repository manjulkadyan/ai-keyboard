/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.translateui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.manjul.aikeyboard.core.translate.LangPair

/**
 * The language pair ("English → Urdu", names from `Locale.getDisplayName`) with the swap button. The arrow is
 * drawn, not a string, so the pair needs no concatenated copy; TalkBack reads `translate_bar__pair_cd`.
 */
@Composable
fun PairChip(
    pair: LangPair,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = pair.source.displayName()
    val target = pair.target.displayName()
    val color = LocalContentColor.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val pairCd = stringResource(R.string.translate_bar__pair_cd, source, target)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier
                .weight(1f, fill = false)
                .semantics(mergeDescendants = true) { contentDescription = pairCd },
            shape = RoundedCornerShape(50),
            color = Color.Transparent,
            contentColor = color,
            border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LanguageName(source, Modifier.weight(1f, fill = false))
                Canvas(Modifier.size(12.dp)) { forwardArrowGlyph(color, rtl) }
                LanguageName(target, Modifier.weight(1f, fill = false))
            }
        }
        GlyphButton(
            label = stringResource(R.string.translate_bar__swap_cd, source, target),
            onClick = onSwap,
        ) { swapGlyph(it) }
    }
}

@Composable
private fun LanguageName(name: String, modifier: Modifier) {
    Text(
        text = name,
        modifier = modifier,
        style = TextStyle(textDirection = TextDirection.Content),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
