/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.aiui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * The inline paywall card shown when the AI quota is used up (PRD FR-29, Sprint 3 decision 14): "Daily AI limit
 * reached · resets in N h" and a "See Pro" button that opens the app's `pro` route.
 */
@Composable
fun QuotaCard(resetsAtEpochMs: Long, nowMs: Long, onSeePro: () -> Unit, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Surface(
        modifier = modifier.fillMaxWidth().testTag(QUOTA_TAG),
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        contentColor = color,
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.ai_sheet__quota, AiStrings.quotaHours(resetsAtEpochMs, nowMs)),
                modifier = Modifier.weight(1f),
            )
            SheetChip(stringResource(R.string.ai_sheet__quota_see_pro), onSeePro)
        }
    }
}

internal const val QUOTA_TAG = "ai_sheet_quota"
