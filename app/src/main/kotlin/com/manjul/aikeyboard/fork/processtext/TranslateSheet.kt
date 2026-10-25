/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.processtext

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.translate.FailKind
import com.manjul.aikeyboard.core.translate.Lang
import java.util.Locale
import org.florisboard.lib.compose.stringRes

/** The sheet's buttons; the activity does the platform work (clipboard, result, TTS install, navigation). */
class TranslateSheetActions(
    val onDismiss: () -> Unit,
    val onPickTarget: (Lang) -> Unit,
    val onRetry: () -> Unit,
    val onDownload: () -> Unit,
    val onCopy: () -> Unit,
    val onReplace: () -> Unit,
    val onSpeak: () -> Unit,
    val onOpenInApp: () -> Unit,
    val onEnableKeyboard: () -> Unit,
    val onDismissEnableKeyboard: () -> Unit,
)

/**
 * The Translate sheet (PRD FR-14): a bottom sheet over a scrim; tapping the scrim closes it. Shows the pair, the
 * translation and Copy, Replace (only when [canReplace]), Speak and Open in app. No ads.
 */
@Composable
fun TranslateSheet(
    state: TranslateSheetState,
    canReplace: Boolean,
    truncated: Boolean,
    targets: List<Lang>,
    actions: TranslateSheetActions,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                actions.onDismiss()
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                // Swallow taps so they do not reach the scrim.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PairRow(state, targets, actions.onPickTarget)
                if (truncated) {
                    Text(stringRes(R.string.process_text__truncated), style = MaterialTheme.typography.bodySmall)
                }
                Body(state, actions)
                Buttons(state, canReplace, actions)
                if (state.showEnableKeyboard) EnableKeyboardCard(actions)
            }
        }
    }
}

@Composable
private fun PairRow(state: TranslateSheetState, targets: List<Lang>, onPickTarget: (Lang) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val target = displayName(state.pair.target)
    val a11y = stringRes(R.string.process_text__target_a11y, "language" to target)
    Box {
        TextButton(onClick = { open = true }, modifier = Modifier.semantics { contentDescription = a11y }) {
            Text(
                stringRes(R.string.process_text__pair, "source" to displayName(state.pair.source), "target" to target),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (lang in targets) {
                DropdownMenuItem(
                    text = { Text(displayName(lang)) },
                    onClick = {
                        open = false
                        onPickTarget(lang)
                    },
                )
            }
        }
    }
}

@Composable
private fun Body(state: TranslateSheetState, actions: TranslateSheetActions) {
    when (val status = state.status) {
        null -> Text(state.translation.orEmpty(), style = MaterialTheme.typography.bodyLarge)
        SheetStatus.Translating -> {
            Text(stringRes(R.string.process_text__translating))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        SheetStatus.Downloading -> {
            Text(stringRes(R.string.process_text__downloading))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        is SheetStatus.NeedsDownload -> {
            Text(stringRes(R.string.process_text__needs_download, "size" to megabytes(status.bytes)))
            OutlinedButton(onClick = actions.onDownload) { Text(stringRes(R.string.process_text__download)) }
        }
        SheetStatus.CloudOnly -> Text(stringRes(R.string.process_text__cloud_only))
        SheetStatus.Unsupported -> Text(stringRes(R.string.process_text__unsupported))
        is SheetStatus.Error -> {
            val message = if (status.kind == FailKind.OFFLINE) R.string.process_text__offline else R.string.process_text__error
            Text(stringRes(message))
            OutlinedButton(onClick = actions.onRetry) { Text(stringRes(R.string.process_text__retry)) }
        }
    }
}

@Composable
private fun Buttons(state: TranslateSheetState, canReplace: Boolean, actions: TranslateSheetActions) {
    val ready = state.status == null && state.translation != null
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (ready) {
            TextButton(onClick = actions.onCopy) { Text(stringRes(R.string.process_text__copy)) }
            if (canReplace) TextButton(onClick = actions.onReplace) { Text(stringRes(R.string.process_text__replace)) }
            TextButton(onClick = actions.onSpeak, enabled = !state.speakUnavailable) {
                Text(stringRes(R.string.process_text__speak))
            }
        }
        TextButton(onClick = actions.onOpenInApp) { Text(stringRes(R.string.process_text__open_in_app)) }
        TextButton(onClick = actions.onDismiss) { Text(stringRes(R.string.process_text__close)) }
    }
    if (state.speakUnavailable) {
        Text(stringRes(R.string.process_text__speak_unavailable), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EnableKeyboardCard(actions: TranslateSheetActions) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringRes(R.string.process_text__enable_keyboard_title), style = MaterialTheme.typography.titleSmall)
            Text(stringRes(R.string.process_text__enable_keyboard_body), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = actions.onEnableKeyboard) { Text(stringRes(R.string.process_text__enable_keyboard)) }
                TextButton(onClick = actions.onDismissEnableKeyboard) { Text(stringRes(R.string.process_text__not_now)) }
            }
        }
    }
}

internal fun displayName(lang: Lang, locale: Locale = Locale.getDefault()): String =
    Locale.forLanguageTag(lang.tag).getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }.ifEmpty { lang.tag }

/** Whole megabytes, at least 1, as the bar's download chip shows them. */
internal fun megabytes(bytes: Long): Int = ((bytes + 500_000L) / 1_000_000L).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
