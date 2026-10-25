/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.translateui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.manjul.aikeyboard.core.translate.LangPair

/**
 * The language pair ("English → Urdu", names from `Locale.getDisplayName`) with the swap button.
 *
 * Scaffold: signature only.
 */
@Composable
fun PairChip(
    pair: LangPair,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier,
) {
}
