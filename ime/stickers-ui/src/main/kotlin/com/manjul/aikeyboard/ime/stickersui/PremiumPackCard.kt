/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.stickersui

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
 * The inline card for a premium sticker pack without Pro (PRD FR-23): "This pack comes with Pro" and a "See Pro"
 * button. Only that button opens the paywall (`paywall?trigger=premium_sticker`); the card never opens it by itself.
 */
@Composable
fun PremiumPackCard(onSeePro: () -> Unit, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Surface(
        modifier = modifier.fillMaxWidth().testTag(PREMIUM_CARD_TAG),
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
            Text(text = "👑 " + stringResource(R.string.stickers__premium_card), modifier = Modifier.weight(1f))
            Surface(
                onClick = onSeePro,
                modifier = Modifier.testTag(SEE_PRO_TAG),
                shape = RoundedCornerShape(50),
                color = Color.Transparent,
                contentColor = color,
                border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
            ) {
                Text(
                    text = stringResource(R.string.stickers__see_pro),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    maxLines = 1,
                )
            }
        }
    }
}

const val PREMIUM_CARD_TAG = "stickers_premium_card"
const val SEE_PRO_TAG = "stickers_see_pro"
