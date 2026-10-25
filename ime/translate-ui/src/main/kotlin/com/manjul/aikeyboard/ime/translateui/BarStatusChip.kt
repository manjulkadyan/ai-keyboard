/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.translateui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.manjul.aikeyboard.core.translate.FailKind
import com.manjul.aikeyboard.core.translate.bar.BarStatus

/**
 * The inline status chip for [status]: "Download …" calls [onDownload], "Offline: download languages" calls
 * [onOpenOfflineLanguages], "Retry" calls [onRetry], "Needs internet (Pro)" and "not supported" are informational.
 * Nothing for `Idle`/`Ok` (and for a policy refusal, which closes the bar anyway).
 */
@Composable
fun BarStatusChip(
    status: BarStatus,
    onDownload: () -> Unit,
    onOpenOfflineLanguages: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (status) {
        BarStatus.Idle, BarStatus.Ok -> Unit
        is BarStatus.NeedsDownload -> BarChip(
            label = stringResource(
                R.string.translate_bar__status_download,
                status.pair.source.displayName(),
                status.pair.target.displayName(),
                downloadMegabytes(status.bytes),
            ),
            onClick = onDownload,
            modifier = modifier,
        )
        BarStatus.Offline -> OfflineChip(onOpenOfflineLanguages, modifier)
        BarStatus.CloudOnlyPro -> CloudProChip(modifier)
        is BarStatus.Error -> when (status.kind) {
            FailKind.NO_PACK, FailKind.OFFLINE -> OfflineChip(onOpenOfflineLanguages, modifier)
            FailKind.CLOUD_UNAVAILABLE -> CloudProChip(modifier)
            FailKind.UNSUPPORTED -> BarChip(stringResource(R.string.translate_bar__status_unsupported), null, modifier)
            FailKind.TIMEOUT, FailKind.QUOTA, FailKind.ENGINE ->
                BarChip(stringResource(R.string.translate_bar__status_retry), onRetry, modifier)
            FailKind.POLICY -> Unit
        }
    }
}

@Composable
private fun OfflineChip(onClick: () -> Unit, modifier: Modifier) =
    BarChip(stringResource(R.string.translate_bar__status_offline), onClick, modifier)

@Composable
private fun CloudProChip(modifier: Modifier) =
    BarChip(stringResource(R.string.translate_bar__status_cloud_pro), null, modifier)

/** The "about N MB" figure: decimal megabytes, rounded, at least 1. */
internal fun downloadMegabytes(bytes: Long): Int =
    ((bytes + 500_000L) / 1_000_000L).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
