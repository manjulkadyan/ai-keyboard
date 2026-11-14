/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.stickers

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.manjul.aikeyboard.FlorisImeService
import com.manjul.aikeyboard.ime.keyboard.FlorisImeSizing
import com.manjul.aikeyboard.ime.media.KeyboardLikeButton
import com.manjul.aikeyboard.ime.stickersui.StickerPanel
import com.manjul.aikeyboard.ime.text.keyboard.TextKeyData
import com.manjul.aikeyboard.ime.theme.FlorisImeUi
import com.manjul.aikeyboard.keyboardManager
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggRow

/** The sticker panel in the keyboard area (ImeUiMode.STICKERS), with the media bottom row (ABC, delete). */
@Composable
fun StickerPanelLayout(modifier: Modifier = Modifier) {
    val session = FlorisImeService.stickersSessionOrNull() ?: return
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val state by session.controller.state.collectAsState()

    SnyggColumn(
        elementName = FlorisImeUi.Media.elementName,
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.imeUiHeight()),
    ) {
        StickerPanel(
            state = state,
            onPack = session.controller::onPackSelected,
            onSticker = session::onStickerTapped,
            onSeePro = session::seePro,
            modifier = Modifier.weight(1f),
        )
        SnyggRow(
            elementName = FlorisImeUi.MediaBottomRow.elementName,
            modifier = Modifier
                .fillMaxWidth()
                .height(FlorisImeSizing.keyboardRowBaseHeight * 0.8f),
        ) {
            KeyboardLikeButton(
                elementName = FlorisImeUi.MediaBottomRowButton.elementName,
                inputEventDispatcher = keyboardManager.inputEventDispatcher,
                keyData = TextKeyData.IME_UI_MODE_TEXT,
                modifier = Modifier.fillMaxHeight(),
            ) {
                Text(text = "ABC", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.weight(1f))
            KeyboardLikeButton(
                elementName = FlorisImeUi.MediaBottomRowButton.elementName,
                inputEventDispatcher = keyboardManager.inputEventDispatcher,
                keyData = TextKeyData.DELETE,
                modifier = Modifier.fillMaxHeight(),
            ) {
                Icon(imageVector = Icons.AutoMirrored.Outlined.Backspace, contentDescription = null)
            }
        }
    }
}
