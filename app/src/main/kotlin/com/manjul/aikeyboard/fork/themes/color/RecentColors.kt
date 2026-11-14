/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.themes.color

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.manjul.aikeyboard.R
import org.florisboard.lib.compose.stringRes

/** The colour picker's recents (S5 Task 8, PRD FR-18 AC2), stored in `FeatureSettings.recentColors`. */
object RecentColors {
    const val MAX: Int = 8

    /** [recents] with [color] in front: newest first, no duplicates, at most [MAX]. */
    fun add(recents: List<Long>, color: Long): List<Long> =
        (listOf(color) + recents).distinct().take(MAX)
}

/** One 48 dp swatch per recent colour; a tap picks it. */
@Composable
internal fun RecentColorsRow(recents: List<Long>, onPick: (Long) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        for (color in recents) {
            val description = stringRes(R.string.color_picker__recent, "hex" to hexOf(color))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(role = Role.Button) { onPick(color) }
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .background(Color(color.toInt()), CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                )
            }
        }
    }
}
