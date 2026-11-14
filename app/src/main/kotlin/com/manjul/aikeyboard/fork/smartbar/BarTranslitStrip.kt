/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.smartbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.manjul.aikeyboard.FlorisImeService
import com.manjul.aikeyboard.fork.translit.BarTranslitController
import com.manjul.aikeyboard.fork.translit.TranslitSuggestionProvider
import com.manjul.aikeyboard.ime.translateui.BarUiState
import com.manjul.aikeyboard.subtypeManager

/**
 * The Roman typing strip for the open Translate bar (S6): while the bar is open in a Roman subtype, the candidates
 * row shows the bar's candidates and host suggestions hide. Null otherwise, so the row keeps its usual list.
 */
@Composable
fun barTranslitStripOrNull(): BarTranslitController? {
    val session = FlorisImeService.translateSessionOrNull() ?: return null
    val controller by session.controllerOrNull.collectAsState()
    val c = controller ?: return null
    val ui by c.ui.collectAsState()
    val subtypeManager by LocalContext.current.subtypeManager()
    val subtype by subtypeManager.activeSubtypeFlow.collectAsState()
    if (ui !is BarUiState.Open || TranslitSuggestionProvider.romanLangOf(subtype) == null) return null
    return session.barTranslit
}
