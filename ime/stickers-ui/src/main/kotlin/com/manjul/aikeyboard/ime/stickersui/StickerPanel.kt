/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.stickersui

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.manjul.aikeyboard.core.content.stickers.StickerUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The sticker panel body (PRD FR-23): a row of pack tabs (a premium pack has a 👑 badge), the premium card when
 * needed, and the selected pack's stickers. [onSticker] gets every tap; the caller asks the controller first.
 */
@Composable
fun StickerPanel(
    state: StickerPanelState,
    onPack: (String) -> Unit,
    onSticker: (StickerUi) -> Unit,
    onSeePro: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = state.selected
    Column(modifier = modifier.fillMaxSize().testTag(PANEL_TAG)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(state.packs, key = { it.id }) { pack ->
                PackTab(
                    title = pack.title,
                    premium = pack.premium,
                    selected = pack.id == selected?.id,
                    onClick = { onPack(pack.id) },
                )
            }
        }
        if (state.premiumCard) {
            PremiumPackCard(onSeePro = onSeePro, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        }
        val notice = when {
            state.installing != null -> stringResource(R.string.stickers__downloading)
            state.installFailed -> stringResource(R.string.stickers__download_failed)
            selected != null && !selected.installed && !state.premiumCard ->
                stringResource(R.string.stickers__not_installed)
            else -> null
        }
        if (notice != null) Text(notice, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(72.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(selected?.stickers.orEmpty(), key = { _, s -> s.id }) { index, sticker ->
                val label = stringResource(R.string.stickers__sticker, index + 1)
                StickerCell(sticker, label) { onSticker(sticker) }
            }
        }
    }
}

@Composable
private fun PackTab(title: String, premium: Boolean, selected: Boolean, onClick: () -> Unit) {
    val color = LocalContentColor.current
    val badge = stringResource(R.string.stickers__premium_badge)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) color.copy(alpha = 0.15f) else Color.Transparent,
        contentColor = color,
        border = BorderStroke(1.dp, color.copy(alpha = if (selected) 0.8f else 0.4f)),
        modifier = if (premium) Modifier.semantics { contentDescription = "$title, $badge" } else Modifier,
    ) {
        Text(
            text = if (premium) "$title 👑" else title,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun StickerCell(sticker: StickerUi, label: String, onClick: () -> Unit) {
    val bitmap by produceState<ImageBitmap?>(null, sticker.file) {
        value = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(sticker.file.path)?.asImageBitmap() }.getOrNull()
        }
    }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
        if (sticker.premium) Text("👑", modifier = Modifier.align(Alignment.TopEnd))
    }
}

const val PANEL_TAG = "stickers_panel"
