/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.aiui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.manjul.aikeyboard.core.ai.AiError
import com.manjul.aikeyboard.core.ai.RewriteMode
import java.util.Locale

// The AI rewrite sheet (PRD FR-15/FR-16, Sprint 3 decisions 13-16). It fills the keyboard area, takes its colors
// from LocalContentColor (the keyboard theme) like the translate bar and voice panel, and never names the AI engine.

/** The sheet bound to [controller]; [onSeePro] opens the app's `pro` route from the quota card. */
@Composable
fun AiSheet(controller: AiActionController, onSeePro: () -> Unit, nowMs: () -> Long, modifier: Modifier = Modifier) {
    val state by controller.state.collectAsState()
    AiSheet(
        state = state,
        actions = AiSheetActions(
            onTone = controller::chooseTone,
            onStop = controller::stop,
            onReplace = controller::replace,
            onInsert = controller::insert,
            onCopy = controller::copy,
            onRetry = controller::retry,
            onUndo = { controller.undo() },
            onReport = controller::openReport,
            onSubmitReport = controller::submitReport,
            onCancelReport = controller::closeReport,
            onSeePro = onSeePro,
            onClose = controller::dismiss,
        ),
        nowMs = nowMs,
        modifier = modifier,
    )
}

/** The sheet bound to the post-dictation [controller] (✦ Polish); same layout, Polish title. */
@Composable
fun AiSheet(controller: PolishController, onSeePro: () -> Unit, nowMs: () -> Long, modifier: Modifier = Modifier) {
    val state by controller.state.collectAsState()
    AiSheet(
        state = state,
        actions = AiSheetActions(
            onTone = {},
            onStop = controller::stop,
            onReplace = controller::replace,
            onInsert = controller::insert,
            onCopy = controller::copy,
            onRetry = controller::retry,
            onUndo = { controller.undo() },
            onReport = controller::openReport,
            onSubmitReport = controller::submitReport,
            onCancelReport = controller::closeReport,
            onSeePro = onSeePro,
            onClose = controller::dismiss,
        ),
        nowMs = nowMs,
        modifier = modifier,
        polish = true,
    )
}

/** What the sheet's buttons do. */
class AiSheetActions(
    val onTone: (RewriteMode) -> Unit,
    val onStop: () -> Unit,
    val onReplace: () -> Unit,
    val onInsert: () -> Unit,
    val onCopy: () -> Unit,
    val onRetry: () -> Unit,
    val onUndo: () -> Unit,
    val onReport: () -> Unit,
    val onSubmitReport: (ReportReason, Boolean) -> Unit,
    val onCancelReport: () -> Unit,
    val onSeePro: () -> Unit,
    val onClose: () -> Unit,
)

/** The stateless sheet: draws [state]; nothing for [AiSheetState.Hidden]. [polish] titles it as ✦ Polish. */
@Composable
fun AiSheet(
    state: AiSheetState,
    actions: AiSheetActions,
    nowMs: () -> Long,
    modifier: Modifier = Modifier,
    polish: Boolean = false,
) {
    if (state == AiSheetState.Hidden) return
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp).testTag(SHEET_TAG)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(if (polish) R.string.polish__title else R.string.ai_sheet__title),
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                fontWeight = FontWeight.Medium,
            )
            SheetGlyphButton(stringResource(R.string.ai_sheet__close_cd), actions.onClose) { closeGlyph(it) }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                AiSheetState.Hidden -> Unit
                is AiSheetState.ChooseTone -> ToneChips(state, actions.onTone)
                is AiSheetState.Streaming -> Output(state.text, footer = {
                    val working = when {
                        state.stillWorking -> R.string.ai_sheet__still_working
                        polish -> R.string.polish__working
                        else -> R.string.ai_sheet__working
                    }
                    StatusText(stringResource(working))
                    SheetChip(stringResource(R.string.ai_sheet__stop), actions.onStop)
                })
                is AiSheetState.Ready -> Output(state.text, footer = {
                    if (state.trimmed) StatusText(stringResource(R.string.ai_sheet__trimmed))
                    SheetChip(stringResource(R.string.ai_sheet__replace), actions.onReplace)
                    SheetChip(stringResource(R.string.ai_sheet__insert), actions.onInsert)
                    SheetChip(stringResource(R.string.ai_sheet__copy), actions.onCopy)
                    SheetChip(stringResource(R.string.ai_sheet__retry), actions.onRetry)
                    OverflowMenu(actions.onReport)
                })
                is AiSheetState.TextChanged -> Output(state.text, header = {
                    StatusText(stringResource(R.string.ai_sheet__text_changed))
                }, footer = {
                    SheetChip(stringResource(R.string.ai_sheet__insert), actions.onInsert)
                    SheetChip(stringResource(R.string.ai_sheet__copy), actions.onCopy)
                    SheetChip(stringResource(R.string.ai_sheet__retry), actions.onRetry)
                })
                is AiSheetState.Error -> ErrorBody(state, actions, nowMs)
                AiSheetState.NeedsConsent -> StatusText(stringResource(R.string.ai_sheet__needs_consent))
                AiSheetState.NothingToRewrite -> StatusText(stringResource(R.string.ai_sheet__nothing))
                is AiSheetState.Replaced -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusText(stringResource(R.string.ai_sheet__replaced))
                    SheetChip(stringResource(R.string.ai_sheet__undo), actions.onUndo)
                }
                is AiSheetState.Reporting -> ReportSheet(actions.onSubmitReport, actions.onCancelReport)
            }
        }
    }
}

@Composable
private fun ToneChips(state: AiSheetState.ChooseTone, onTone: (RewriteMode) -> Unit) {
    val target = state.translateTarget?.let { languageName(it) }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (mode in state.tones) {
            val label = if (mode == RewriteMode.TRANSLATE) {
                if (target == null) continue
                stringResource(AiStrings.tone(mode), target)
            } else {
                stringResource(AiStrings.tone(mode))
            }
            SheetChip(label, { onTone(mode) })
        }
    }
}

/** The preview: user text, laid out by its own direction (RTL output reads right to left). */
@Composable
private fun Output(
    text: String,
    header: @Composable () -> Unit = {},
    footer: @Composable () -> Unit,
) {
    val description = stringResource(R.string.ai_sheet__preview_cd)
    Column(modifier = Modifier.fillMaxSize()) {
        header()
        Text(
            text = text,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(4.dp)
                .semantics {
                    contentDescription = description
                    liveRegion = LiveRegionMode.Polite
                }
                .testTag(PREVIEW_TAG),
            style = TextStyle(textDirection = TextDirection.Content),
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { footer() }
    }
}

@Composable
private fun ErrorBody(state: AiSheetState.Error, actions: AiSheetActions, nowMs: () -> Long) {
    val error = state.error
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (error is AiError.Quota) {
            QuotaCard(error.resetsAtEpochMs, nowMs(), actions.onSeePro)
            return@Column
        }
        val message = if (error is AiError.RateLimited) {
            stringResource(AiStrings.error(error), error.retryAfterS.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt())
        } else {
            stringResource(AiStrings.error(error))
        }
        StatusText(message)
        if (state.canRetry) SheetChip(stringResource(R.string.ai_sheet__retry), actions.onRetry)
    }
}

@Composable
private fun OverflowMenu(onReport: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        SheetGlyphButton(stringResource(R.string.ai_sheet__more_cd), { open = true }) { moreGlyph(it) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.ai_sheet__report)) },
                onClick = {
                    open = false
                    onReport()
                },
            )
        }
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(4.dp).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** The language's name in the UI language, from the system, never from our strings. */
@Composable
@ReadOnlyComposable
internal fun languageName(tag: String): String {
    val ui = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    return Locale.forLanguageTag(tag).getDisplayName(ui).ifBlank { tag }
}

/** An outlined pill (matches the translate bar's `BarChip`); without [onClick] it is shown disabled. */
@Composable
internal fun SheetChip(label: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        contentColor = if (onClick != null) color else color.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, color.copy(alpha = if (onClick != null) 0.6f else 0.3f)),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SheetGlyphButton(label: String, onClick: () -> Unit, glyph: DrawScope.(Color) -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        val color = LocalContentColor.current
        Canvas(Modifier.size(16.dp)) { glyph(color) }
    }
}

private fun DrawScope.closeGlyph(color: Color) {
    val stroke = 2.dp.toPx()
    val inset = size.minDimension * 0.125f
    drawLine(color, Offset(inset, inset), Offset(size.width - inset, size.height - inset), stroke, StrokeCap.Round)
    drawLine(color, Offset(size.width - inset, inset), Offset(inset, size.height - inset), stroke, StrokeCap.Round)
}

private fun DrawScope.moreGlyph(color: Color) {
    val r = 1.6.dp.toPx()
    val x = size.width / 2
    for (i in 0..2) drawCircle(color, r, Offset(x, size.height * (0.2f + 0.3f * i)))
}

internal const val SHEET_TAG = "ai_sheet"
internal const val PREVIEW_TAG = "ai_sheet_preview"
