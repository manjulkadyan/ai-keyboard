/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.aiui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** The ✦ Polish chip after a dictation: "Clean up", and "Clean up + translate to [targetLang]" when [canTranslate]. */
data class PolishChipState(val canTranslate: Boolean, val targetLang: String?)

/** The chips in the Smartbar row (colors from LocalContentColor, like the sheet). Holds no user text. */
@Composable
fun PolishChip(
    state: PolishChipState,
    onCleanUp: () -> Unit,
    onCleanUpAndTranslate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).testTag(POLISH_CHIP_TAG),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SheetChip(stringResource(R.string.polish__clean_up), onCleanUp)
        val target = state.targetLang
        if (state.canTranslate && target != null) {
            SheetChip(stringResource(R.string.polish__clean_up_translate, languageName(target)), onCleanUpAndTranslate)
        }
    }
}

/** The chip bound to [controller]. */
@Composable
fun PolishChip(controller: PolishController, state: PolishChipState, modifier: Modifier = Modifier) {
    PolishChip(
        state = state,
        onCleanUp = { controller.polish(translate = false) },
        onCleanUpAndTranslate = { controller.polish(translate = true) },
        modifier = modifier,
    )
}

/** After an auto-polish replaced the dictation: a light "Undo" chip in the Smartbar row (never the sheet). */
@Composable
fun PolishUndoChip(onUndo: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.testTag(POLISH_UNDO_CHIP_TAG),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SheetChip(stringResource(R.string.ai_sheet__undo), onUndo)
    }
}

internal const val POLISH_CHIP_TAG = "polish_chip"

internal const val POLISH_UNDO_CHIP_TAG = "polish_undo_chip"
